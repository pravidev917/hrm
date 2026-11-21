#!/usr/bin/env bash
# Deploy the SPA bundle on the server.
#
#   sudo deploy/scripts/deploy-frontend.sh /tmp/release/<stamp>/dist
#
# This script moves and deletes directories, so it refuses to guess:
#   * the target must be an absolute path, at least two levels deep, not a system
#     directory and not a symlink;
#   * an existing target we did not create (no .hrm-release marker) is refused
#     unless HRM_ALLOW_UNMARKED=1 is passed explicitly;
#   * the incoming bundle must look like a Vite build, and every /assets/... file
#     referenced by index.html must be present;
#   * a lock directory makes concurrent runs fail fast instead of interleaving;
#   * staging and previous-release paths carry a timestamp, the PID and are created
#     with mkdir (which fails if they exist) - nothing is ever removed just because
#     its name looks familiar;
#   * ownership and permissions must be applied; a failure aborts the deploy;
#   * after the swap the live bundle is checked again (index.html, marker, assets,
#     owner) and the previous release is restored if it does not pass;
#   * rotation only deletes directories that match the exact release pattern AND
#     carry the marker, keeping the newest HRM_KEEP_RELEASES.
#
# Exit codes: 0 deployed and verified; 1 refused before any change; 3 swapped but
# verification failed and the previous release was restored (or could not be).
#
# Environment: HRM_WEB_ROOT HRM_WEB_USER HRM_KEEP_RELEASES HRM_ALLOW_UNMARKED
set -euo pipefail
umask 022

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/pg-env.sh
. "$SCRIPT_DIR/lib/pg-env.sh"      # cleanup registry + shared helpers

NEW_DIST="${1:-}"
WEB_ROOT="${HRM_WEB_ROOT:-/var/www/hrm}"
WEB_USER="${HRM_WEB_USER:-www-data}"
KEEP="${HRM_KEEP_RELEASES:-3}"
ALLOW_UNMARKED="${HRM_ALLOW_UNMARKED:-0}"
MARKER=".hrm-release"

die() { printf '!! %s\n' "$*" >&2; exit 1; }
say() { printf '%s\n' "$*"; }

# --- validation -------------------------------------------------------------
[[ -n "$NEW_DIST" ]] || { printf '!! usage: deploy-frontend.sh <path-to-dist>\n' >&2; exit 1; }

case "$WEB_ROOT" in
  /*) ;;
  *) die "HRM_WEB_ROOT must be an absolute path (got: $WEB_ROOT)" ;;
esac
case "$WEB_ROOT" in
  *..*|*'//'*|*/) die "HRM_WEB_ROOT contains an unsafe path element: $WEB_ROOT" ;;
esac
case "$WEB_ROOT" in
  /|/bin|/boot|/dev|/etc|/home|/lib|/lib64|/media|/mnt|/opt|/proc|/root|/run|/sbin|/srv|/sys|/tmp|/usr|/var|/var/www|/var/www/html)
    die "refusing to use $WEB_ROOT as the web root" ;;
esac
DEPTH="$(printf '%s' "$WEB_ROOT" | awk -F/ '{print NF-1}')"
[[ "$DEPTH" -ge 2 ]] || die "HRM_WEB_ROOT is too shallow ($WEB_ROOT); expected something like /var/www/hrm"
[[ ! -L "$WEB_ROOT" ]] || die "$WEB_ROOT is a symlink; deploy to the real directory instead"
[[ "$KEEP" =~ ^[0-9]+$ ]] || die "HRM_KEEP_RELEASES must be a number (got: $KEEP)"
[[ "$KEEP" -ge 1 && "$KEEP" -le 100 ]] || die "HRM_KEEP_RELEASES must be between 1 and 100 (got: $KEEP)"
[[ "$ALLOW_UNMARKED" == "0" || "$ALLOW_UNMARKED" == "1" ]] || die "HRM_ALLOW_UNMARKED must be 0 or 1"

id -u "$WEB_USER" >/dev/null 2>&1 \
  || die "web user '$WEB_USER' does not exist; refusing to deploy a bundle nobody can own/serve"

[[ -d "$NEW_DIST" ]] || die "not a directory: $NEW_DIST"
NEW_DIST_REAL="$(cd "$NEW_DIST" && pwd -P)"
[[ "$NEW_DIST_REAL" != "$WEB_ROOT" ]] || die "source and destination are the same directory"
[[ -f "$NEW_DIST_REAL/index.html" ]] || die "$NEW_DIST has no index.html - not a Vite build"
[[ -s "$NEW_DIST_REAL/index.html" ]] || die "$NEW_DIST/index.html is empty"
[[ -d "$NEW_DIST_REAL/assets" ]] || die "$NEW_DIST has no assets/ directory"
ASSET_COUNT="$(find "$NEW_DIST_REAL/assets" -maxdepth 1 -type f | wc -l | tr -d ' ')"
[[ "$ASSET_COUNT" -gt 0 ]] || die "$NEW_DIST/assets is empty"

INCOMPLETE=0
while read -r ref; do
  [[ -z "$ref" ]] && continue
  [[ -e "$NEW_DIST_REAL/$ref" ]] || { echo "!! referenced asset missing from the bundle: $ref" >&2; INCOMPLETE=1; }
done < <(grep -oE '/assets/[A-Za-z0-9._-]+' "$NEW_DIST_REAL/index.html" | sort -u)
[[ "$INCOMPLETE" -eq 0 ]] || die "the bundle is incomplete; refusing to deploy it"

if [[ -e "$WEB_ROOT" ]]; then
  [[ -d "$WEB_ROOT" ]] || die "$WEB_ROOT exists and is not a directory"
  if [[ -n "$(ls -A "$WEB_ROOT" 2>/dev/null)" && ! -f "$WEB_ROOT/$MARKER" && "$ALLOW_UNMARKED" != "1" ]]; then
    die "$WEB_ROOT is not empty and has no $MARKER marker (not deployed by this script). \
Re-run with HRM_ALLOW_UNMARKED=1 only if you are certain its contents are expendable."
  fi
fi

# --- lock -------------------------------------------------------------------
LOCK_DIR="${WEB_ROOT}.lock"
LOCK_PARENT="$(dirname "$WEB_ROOT")"
mkdir -p "$LOCK_PARENT" 2>/dev/null || die "cannot create $LOCK_PARENT (run with sudo)"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  if [[ -d "$LOCK_DIR" ]]; then
    HOLDER="$(cat "$LOCK_DIR/pid" 2>/dev/null || echo unknown)"
    die "another deployment appears to be running (lock $LOCK_DIR, pid $HOLDER). \
If you are sure nothing is running, remove the lock directory manually: rm -rf $LOCK_DIR"
  fi
  die "cannot create the lock directory $LOCK_DIR (permissions?)"
fi
printf '%s\n' "$$" > "$LOCK_DIR/pid"

release_lock() { [[ -d "$LOCK_DIR" ]] && rm -rf -- "$LOCK_DIR"; return 0; }
hrm_cleanup_register release_lock

STAMP="$(date +%Y%m%d-%H%M%S)"
STAGE="${WEB_ROOT}.new-$STAMP-$$"
PREV="${WEB_ROOT}.prev-$STAMP-$$"

cleanup_stage() {
  # only ever remove our own staging directory, never a pre-existing one
  [[ -n "${STAGE:-}" && -d "${STAGE:-}" && "$STAGE" == "$WEB_ROOT".new-* ]] && rm -rf -- "$STAGE"
  return 0
}
hrm_cleanup_register cleanup_stage

[[ ! -e "$STAGE" ]] || die "staging path already exists: $STAGE (previous run still active?)"
[[ ! -e "$PREV" ]] || die "previous-release path already exists: $PREV (previous run still active?)"

say "== deploy frontend: $NEW_DIST_REAL -> $WEB_ROOT =="

# --- stage ------------------------------------------------------------------
mkdir "$STAGE" || die "cannot create staging directory $STAGE"
cp -a -- "$NEW_DIST_REAL"/. "$STAGE"/ || die "copy into $STAGE failed"
{
  echo "deployed=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "source=$NEW_DIST_REAL"
  echo "assets=$ASSET_COUNT"
  echo "pid=$$"
} > "$STAGE/$MARKER"

chown -R "$WEB_USER":"$WEB_USER" -- "$STAGE" 2>/dev/null \
  || die "cannot chown the staged bundle to $WEB_USER:$WEB_USER - aborting before the live site changes"
chmod -R u=rwX,go=rX -- "$STAGE" 2>/dev/null \
  || die "cannot set permissions on the staged bundle - aborting before the live site changes"

# stage self-check before the swap
[[ -f "$STAGE/index.html" && -s "$STAGE/index.html" ]] || die "staged bundle has no usable index.html"
[[ -f "$STAGE/$MARKER" ]] || die "staged bundle has no $MARKER marker"
while read -r ref; do
  [[ -z "$ref" ]] && continue
  [[ -e "$STAGE/$ref" ]] || die "staged bundle is missing referenced asset: $ref"
done < <(grep -oE '/assets/[A-Za-z0-9._-]+' "$STAGE/index.html" | sort -u)

# --- swap -------------------------------------------------------------------
if [[ -e "$WEB_ROOT" ]]; then
  say "-- moving the current bundle to $(basename "$PREV")"
  mv -- "$WEB_ROOT" "$PREV" || { rm -rf -- "$STAGE"; die "could not move the current bundle aside"; }
fi
if ! mv -- "$STAGE" "$WEB_ROOT"; then
  echo "!! could not move the staged bundle into place; restoring the previous release" >&2
  [[ -e "$PREV" ]] && mv -- "$PREV" "$WEB_ROOT"
  exit 1
fi

# --- post-deployment verification ------------------------------------------
post_check_failed() {
  local reason="$1" failed_dir="${WEB_ROOT}.failed-$STAMP-$$"
  echo "!! post-deployment check failed: $reason" >&2
  echo "!! moving the broken bundle to $(basename "$failed_dir") and restoring the previous release" >&2
  mv -- "$WEB_ROOT" "$failed_dir" || true
  if [[ -e "$PREV" ]]; then
    mv -- "$PREV" "$WEB_ROOT" || echo "!! could not restore $PREV - the site is DOWN" >&2
  else
    echo "!! there was no previous bundle: the web root is now empty" >&2
  fi
  exit 3
}

[[ -f "$WEB_ROOT/index.html" && -s "$WEB_ROOT/index.html" ]] || post_check_failed "index.html missing or empty"
[[ -f "$WEB_ROOT/$MARKER" ]] || post_check_failed "$MARKER marker missing"
grep -q '^source=' "$WEB_ROOT/$MARKER" || post_check_failed "release marker has no source entry"
while read -r ref; do
  [[ -z "$ref" ]] && continue
  [[ -e "$WEB_ROOT/$ref" ]] || post_check_failed "referenced asset missing after the swap: $ref"
done < <(grep -oE '/assets/[A-Za-z0-9._-]+' "$WEB_ROOT/index.html" | sort -u)
OWNER_NOW="$(stat -c '%U' "$WEB_ROOT/index.html" 2>/dev/null || echo unknown)"
[[ "$OWNER_NOW" == "unknown" || "$OWNER_NOW" == "$WEB_USER" ]] \
  || post_check_failed "index.html is owned by '$OWNER_NOW' instead of '$WEB_USER'"

say "OK: $(find "$WEB_ROOT" -type f | wc -l | tr -d ' ') files live in $WEB_ROOT (owner $WEB_USER, marker present)"
[[ -e "$PREV" ]] && say "-- previous bundle kept at $PREV"

# --- rotation (only our own, marked releases) ------------------------------
mapfile -t PREV_DIRS < <(find "$(dirname "$WEB_ROOT")" -maxdepth 1 -type d \
                            -name "$(basename "$WEB_ROOT").prev-*" -printf '%T@ %p\n' 2>/dev/null |
                            sort -rn | cut -d' ' -f2-)
PATTERN="^$(basename "$WEB_ROOT")\.prev-[0-9]{8}-[0-9]{6}(-[0-9]+)?$"
KEPT=0
for dir in "${PREV_DIRS[@]:-}"; do
  [[ -n "$dir" && -d "$dir" ]] || continue
  [[ -L "$dir" ]] && { say "Rotation: keeping $dir (symlink, not ours)"; continue; }
  if ! printf '%s' "$(basename "$dir")" | grep -qE "$PATTERN"; then
    say "Rotation: keeping $dir (does not match the release pattern)"
    continue
  fi
  if [[ ! -f "$dir/$MARKER" || ! -f "$dir/index.html" ]]; then
    say "Rotation: keeping $dir (no $MARKER marker or no index.html, not a verified release)"
    continue
  fi
  KEPT=$((KEPT + 1))
  if [[ "$KEPT" -le "$KEEP" ]]; then
    continue
  fi
  rm -rf -- "$dir" && say "Rotation: removed $dir"
done

LEFT_STAGES="$(find "$(dirname "$WEB_ROOT")" -maxdepth 1 -type d -name "$(basename "$WEB_ROOT").new-*" 2>/dev/null | wc -l | tr -d ' ')"
[[ "$LEFT_STAGES" -gt 0 ]] && say "-- note: $LEFT_STAGES stale staging director(ies) left from earlier runs; inspect and remove manually"

say "Done. Next: curl -fsS http://127.0.0.1/ -o /dev/null && echo frontend-ok"
