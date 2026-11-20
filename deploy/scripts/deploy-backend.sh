#!/usr/bin/env bash
# Deploy a backend jar on the server.
#
#   sudo deploy/scripts/deploy-backend.sh /tmp/release/<stamp>/backend.jar
#
# Pre-flight (all fatal, before the service is touched):
#   * the jar exists, is non-empty and starts with the zip magic "PK";
#   * curl is installed - a health gate that cannot run is not a passing gate;
#   * HRM_HEALTH_URL is a http(s) URL and the wait parameters are numbers;
#   * the service account exists (or HRM_SKIP_OWNER=1 says this is a test box) and
#     every artifact can actually be chowned to it.
#
# Deploy: keep the running jar -> install -> restart -> health gate.
#
# Every failed restart or failed health gate enters the same controlled rollback:
#   * restart failure and unhealthy service are treated identically;
#   * previous jar exists -> it is reinstalled, restarted and re-checked;
#   * no previous jar (first deploy) -> the broken jar is moved aside, the service
#     is stopped and the manual recovery steps are printed;
#   * every failed release is kept under a unique backend.jar.failed-<stamp> name,
#     so earlier evidence is never overwritten.
#
# Exit codes: 0 deployed and healthy; 1 refused before any change; 2 rolled back,
# previous release healthy again; 3 service is NOT healthy (manual action needed).
#
# Environment: HRM_SERVICE HRM_JAR_DIR HRM_USER HRM_HEALTH_URL HRM_WAIT_ATTEMPTS
#              HRM_WAIT_SECONDS HRM_SKIP_OWNER
set -euo pipefail
umask 022

NEW_JAR="${1:-}"
SERVICE="${HRM_SERVICE:-hrm-backend}"
JAR_DIR="${HRM_JAR_DIR:-/opt/hrm/backend}"
SERVICE_USER="${HRM_USER:-hrm}"
HEALTH_URL="${HRM_HEALTH_URL:-http://127.0.0.1:8080/api/health}"
ATTEMPTS="${HRM_WAIT_ATTEMPTS:-30}"
INTERVAL="${HRM_WAIT_SECONDS:-2}"
SKIP_OWNER="${HRM_SKIP_OWNER:-0}"

die() { printf '!! %s\n' "$*" >&2; exit 1; }
say() { printf '%s\n' "$*"; }

# --- pre-flight -------------------------------------------------------------
[[ -n "$NEW_JAR" ]] || { printf '!! usage: deploy-backend.sh <path-to-backend.jar>\n' >&2; exit 1; }
[[ -f "$NEW_JAR" ]] || die "jar not found: $NEW_JAR"
[[ -r "$NEW_JAR" ]] || die "jar is not readable: $NEW_JAR"
[[ -s "$NEW_JAR" ]] || die "jar is empty: $NEW_JAR"

MAGIC="$(head -c 2 -- "$NEW_JAR" 2>/dev/null || true)"
[[ "$MAGIC" == "PK" ]] || die "$NEW_JAR is not a jar/zip archive (magic: '$MAGIC')"

command -v curl >/dev/null 2>&1 \
  || die "curl is not installed: the health gate cannot run, so this script will not deploy. Install curl first."
case "$HEALTH_URL" in
  http://*|https://*) ;;
  *) die "HRM_HEALTH_URL must start with http:// or https:// (got: $HEALTH_URL)" ;;
esac
[[ "$ATTEMPTS" =~ ^[0-9]+$ ]] && [[ "$ATTEMPTS" -ge 1 ]] \
  || die "HRM_WAIT_ATTEMPTS must be a positive integer (got: $ATTEMPTS)"
[[ "$INTERVAL" =~ ^[0-9]+$ ]] \
  || die "HRM_WAIT_SECONDS must be a non-negative integer (got: $INTERVAL)"

[[ -d "$JAR_DIR" ]] || die "target directory does not exist: $JAR_DIR"
[[ -w "$JAR_DIR" ]] || die "cannot write to $JAR_DIR (run with sudo)"

if id -u "$SERVICE_USER" >/dev/null 2>&1; then
  OWNERSHIP_REQUIRED=1
elif [[ "$SKIP_OWNER" == "1" ]]; then
  OWNERSHIP_REQUIRED=0
  say "-- warning: service user '$SERVICE_USER' does not exist; ownership checks skipped (HRM_SKIP_OWNER=1, test use only)"
else
  die "service user '$SERVICE_USER' does not exist. Create it (see docs) or set HRM_SKIP_OWNER=1 on a test box only."
fi

CURRENT="$JAR_DIR/backend.jar"
PREVIOUS="$JAR_DIR/backend.jar.prev"
STAMP="$(date +%Y%m%d-%H%M%S)"

set_owner() {
  [[ "$OWNERSHIP_REQUIRED" -eq 1 ]] || return 0
  chown "$SERVICE_USER":"$SERVICE_USER" -- "$1" 2>/dev/null \
    || die "cannot set ownership of $1 to $SERVICE_USER:$SERVICE_USER - aborting before the service is touched"
}

is_jar() {
  [[ -f "$1" && -s "$1" ]] || return 1
  [[ "$(head -c 2 -- "$1" 2>/dev/null || true)" == "PK" ]]
}

unique_failed_name() {
  local base="$JAR_DIR/backend.jar.failed-$STAMP" candidate="$JAR_DIR/backend.jar.failed-$STAMP" n=0
  while [[ -e "$candidate" ]]; do
    n=$((n + 1))
    candidate="$base-$n"
    [[ "$n" -le 200 ]] || die "cannot find a free name for the failed-release copy"
  done
  printf '%s' "$candidate"
}

health_ok() {
  local i
  for ((i = 1; i <= ATTEMPTS; i++)); do
    if curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null | grep -q '"UP"'; then
      return 0
    fi
    [[ "$INTERVAL" -gt 0 ]] && sleep "$INTERVAL"
  done
  return 1
}

rollback() {
  local reason="$1" failed_copy

  say "!! $reason" >&2
  say "!! entering the rollback path" >&2

  failed_copy="$(unique_failed_name)"
  if cp -p -- "$CURRENT" "$failed_copy" 2>/dev/null; then
    set_owner "$failed_copy" || true
    say "-- failed release kept at $failed_copy (diagnostic copy, nothing overwritten)"
  else
    say "!! could not keep a copy of the failed jar" >&2
  fi

  if [[ "$HAD_PREVIOUS" -eq 1 ]] && is_jar "$PREVIOUS"; then
    say "-- reinstalling the previous release from $PREVIOUS"
    install -m 0644 -- "$PREVIOUS" "$CURRENT" || die "rollback: could not reinstall $PREVIOUS"
    set_owner "$CURRENT"
    if ! systemctl restart "$SERVICE"; then
      say "!! ROLLBACK FAILED: systemctl restart $SERVICE failed. The previous jar is installed at $CURRENT but the service is not running." >&2
      say "   Check: journalctl -u $SERVICE -n 120 --no-pager" >&2
      exit 3
    fi
    if health_ok; then
      say "ROLLED BACK: previous release is installed and healthy ($HEALTH_URL). Failed jar kept at $failed_copy."
      exit 2
    fi
    say "!! ROLLBACK FAILED: the previous jar was installed and restarted, but $HEALTH_URL is not UP." >&2
    say "   Installed: $CURRENT (previous release). Failed release copy: $failed_copy" >&2
    say "   Check: journalctl -u $SERVICE -n 120 --no-pager" >&2
    exit 3
  fi

  if [[ "$HAD_PREVIOUS" -eq 1 ]]; then
    say "!! the previous jar at $PREVIOUS is not a valid archive: it cannot be used for a rollback" >&2
  fi
  say "-- no usable previous jar: stopping $SERVICE instead of leaving it restart-looping" >&2
  systemctl stop "$SERVICE" 2>/dev/null || true
  rm -f -- "$CURRENT" 2>/dev/null || true
  say "" >&2
  say "SERVICE STATUS: stopped. No previous release exists to fall back to." >&2
  say "MANUAL ACTION REQUIRED (first deploy failed):" >&2
  say "  1. journalctl -u $SERVICE -n 120 --no-pager" >&2
  say "  2. fix the cause (env file, database reachability, JWT_SECRET length, port)" >&2
  say "  3. install a corrected jar as $CURRENT and run: systemctl start $SERVICE" >&2
  say "  4. the failed artifact is preserved at $failed_copy" >&2
  exit 3
}

# --- deploy -----------------------------------------------------------------
say "== deploy backend: $(basename "$NEW_JAR") -> $CURRENT =="
say "-- sha256: $(sha256sum "$NEW_JAR" | cut -d' ' -f1)"

HAD_PREVIOUS=0
if is_jar "$CURRENT"; then
  say "-- keeping the running jar as $(basename "$PREVIOUS")"
  cp -p -- "$CURRENT" "$PREVIOUS" || die "could not save the previous jar to $PREVIOUS"
  set_owner "$PREVIOUS"
  HAD_PREVIOUS=1
elif [[ -e "$CURRENT" ]]; then
  die "$CURRENT exists but is not a readable jar archive; inspect it before deploying"
else
  say "-- no existing jar: first-time deploy, there is no rollback target"
fi

say "-- installing the new jar"
install -m 0644 -- "$NEW_JAR" "$CURRENT" || die "install failed for $CURRENT"
set_owner "$CURRENT"

say "-- restarting $SERVICE"
systemctl restart "$SERVICE" || rollback "systemctl restart $SERVICE failed"

if health_ok; then
  say "OK: $SERVICE is healthy ($HEALTH_URL)"
  systemctl --no-pager --lines=8 status "$SERVICE" || true
  exit 0
fi

rollback "$SERVICE did not report UP on $HEALTH_URL within $((ATTEMPTS * INTERVAL))s"
