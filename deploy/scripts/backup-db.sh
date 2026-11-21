#!/usr/bin/env bash
# Logical backup of the HRM database.
#
#   sudo deploy/scripts/backup-db.sh
#   HRM_BACKUP_DIR=/var/backups/hrm HRM_BACKUP_RETENTION_DAYS=30 deploy/scripts/backup-db.sh
#
# Guarantees:
#   * no credential in this script, in argv or in the output - the password travels
#     in a temporary 0600 PGPASSFILE that is removed on success, failure, SIGINT and
#     SIGTERM (single cleanup chain, see lib/pg-env.sh);
#   * DB_URL stays a JDBC URL; host/port/database are parsed out of it for libpq;
#   * the dump is written to a hidden, process-unique ".partial" file and only
#     renamed once pg_restore can read it back. A failed or interrupted run leaves
#     existing backups untouched and no half-written file behind;
#   * backup names never collide: if the timestamp is already taken a numeric
#     suffix is added, never an overwrite;
#   * the result is chmod 600 and asserted, and retention runs only after a
#     verified dump, keeping at least HRM_BACKUP_MIN_KEEP files.
#
# Prints "BACKUP_FILE=<path>" on success so other scripts can consume it.
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/pg-env.sh
. "$SCRIPT_DIR/lib/pg-env.sh"

ENV_FILE="${HRM_ENV_FILE:-/etc/hrm/backend.env}"
BACKUP_DIR="${HRM_BACKUP_DIR:-/var/backups/hrm}"
RETENTION_DAYS="${HRM_BACKUP_RETENTION_DAYS:-30}"
MIN_KEEP="${HRM_BACKUP_MIN_KEEP:-3}"
ALLOW_EMPTY="${HRM_BACKUP_ALLOW_EMPTY:-0}"
PERMISSION_CHECK="${HRM_SKIP_PERMISSION_CHECK:-0}"

if [[ "$ALLOW_EMPTY" == "1" ]]; then
  MIN_BYTES="${HRM_BACKUP_MIN_BYTES:-256}"
else
  MIN_BYTES="${HRM_BACKUP_MIN_BYTES:-1024}"
fi

# --- validate settings ------------------------------------------------------
case "$BACKUP_DIR" in
  /*) ;;
  *) hrm_die "HRM_BACKUP_DIR must be an absolute path (got: $BACKUP_DIR)" ;;
esac
case "$BACKUP_DIR" in
  /|/bin|/boot|/dev|/etc|/home|/lib|/lib64|/media|/mnt|/opt|/proc|/root|/run|/sbin|/srv|/sys|/tmp|/usr|/usr/local|/var|/var/www|/var/www/html)
    hrm_die "refusing to use $BACKUP_DIR as a backup directory" ;;
esac
[[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]] || hrm_die "HRM_BACKUP_RETENTION_DAYS must be a non-negative number"
[[ "$MIN_KEEP" =~ ^[0-9]+$ ]] || hrm_die "HRM_BACKUP_MIN_KEEP must be a non-negative number"
[[ "$MIN_KEEP" -ge 1 ]] || hrm_die "HRM_BACKUP_MIN_KEEP must be at least 1 (keeps the newest backup)"
[[ "$MIN_KEEP" -le 100 ]] || hrm_die "HRM_BACKUP_MIN_KEEP is unreasonably large ($MIN_KEEP)"
[[ "$MIN_BYTES" =~ ^[0-9]+$ ]] || hrm_die "HRM_BACKUP_MIN_BYTES must be a non-negative number"
[[ "$ALLOW_EMPTY" == "0" || "$ALLOW_EMPTY" == "1" ]] || hrm_die "HRM_BACKUP_ALLOW_EMPTY must be 0 or 1"

mode_of() { stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1" 2>/dev/null || echo "unknown"; }

mkdir -p -- "$BACKUP_DIR" || hrm_die "cannot create $BACKUP_DIR"
[[ -d "$BACKUP_DIR" && -w "$BACKUP_DIR" ]] || hrm_die "$BACKUP_DIR is not a writable directory"

DIR_MODE="$(mode_of "$BACKUP_DIR")"
if [[ "$DIR_MODE" != "unknown" ]]; then
  if (( 8#$DIR_MODE & 8#002 )); then
    hrm_die "$BACKUP_DIR is world-writable (mode $DIR_MODE); fix it with: chmod 750 $BACKUP_DIR"
  fi
  if [[ "$DIR_MODE" != "750" && "$DIR_MODE" != "700" ]]; then
    hrm_note "-- note: $BACKUP_DIR is mode $DIR_MODE (expected 750); tightening"
    chmod 750 -- "$BACKUP_DIR" 2>/dev/null || hrm_die "cannot restrict permissions on $BACKUP_DIR"
  fi
fi

# --- connection -------------------------------------------------------------
hrm_pg_prepare "$ENV_FILE" pg_dump pg_restore

# --- collision-free target name --------------------------------------------
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT=""
SUFFIX=0
while :; do
  CANDIDATE="$BACKUP_DIR/hrm_prod-$STAMP"
  [[ "$SUFFIX" -gt 0 ]] && CANDIDATE="$CANDIDATE-$SUFFIX"
  CANDIDATE="$CANDIDATE.dump"
  if [[ ! -e "$CANDIDATE" ]]; then
    OUT="$CANDIDATE"
    break
  fi
  SUFFIX=$((SUFFIX + 1))
  [[ "$SUFFIX" -le 200 ]] || hrm_die "cannot find a free backup file name for timestamp $STAMP"
done
PARTIAL="$BACKUP_DIR/.hrm_prod-$STAMP-$$.dump.partial"

cleanup_partial() {
  [[ -n "${PARTIAL:-}" && -f "${PARTIAL:-}" ]] && rm -f -- "$PARTIAL"
  return 0
}
hrm_cleanup_register cleanup_partial

echo "== pg_dump -Fc $PGDATABASE -> $OUT =="

if ! pg_dump --format=custom --no-owner --no-privileges --file="$PARTIAL" ; then
  hrm_die "pg_dump failed; existing backups were not touched"
fi

# --- verification (nothing is renamed or deleted before this passes) --------
[[ -s "$PARTIAL" ]] || hrm_die "dump file is empty; existing backups were not touched"

SIZE_BYTES="$(wc -c < "$PARTIAL" | tr -d ' ')"
if [[ "$SIZE_BYTES" -lt "$MIN_BYTES" ]]; then
  hrm_die "dump is only ${SIZE_BYTES} bytes (< ${MIN_BYTES}); treating as failure, existing backups untouched"
fi

if ! pg_restore --list "$PARTIAL" >/dev/null 2>&1; then
  hrm_die "dump is not readable by pg_restore; existing backups were not touched"
fi

OBJECTS="$(pg_restore --list "$PARTIAL" 2>/dev/null | grep -vcE '^\s*(;|$)' || true)"
TABLES="$(pg_restore --list "$PARTIAL" 2>/dev/null | grep -c 'TABLE DATA' || true)"

if [[ "${OBJECTS:-0}" -eq 0 && "$ALLOW_EMPTY" != "1" ]]; then
  hrm_die "dump contains no restorable objects; existing backups were not touched"
fi
if [[ "${TABLES:-0}" -eq 0 ]]; then
  if [[ "$ALLOW_EMPTY" == "1" ]]; then
    echo "-- note: source database has no table data (empty target); dump is still valid"
  else
    hrm_die "dump contains no table data (wrong database or empty schema?); existing backups were not touched"
  fi
fi

chmod 600 -- "$PARTIAL" 2>/dev/null || true
FILE_MODE="$(mode_of "$PARTIAL")"
if [[ "$FILE_MODE" != "600" ]]; then
  if [[ "$PERMISSION_CHECK" == "1" ]]; then
    hrm_note "-- warning: dump mode is $FILE_MODE (filesystem cannot express 600); HRM_SKIP_PERMISSION_CHECK=1"
  else
    hrm_die "could not restrict the dump to mode 600 (got $FILE_MODE); refusing to publish it. \
Set HRM_SKIP_PERMISSION_CHECK=1 only on a non-POSIX filesystem such as a Windows build host."
  fi
fi

# `mv -n` refuses to replace an existing file; the name was chosen to be free.
mv -n -- "$PARTIAL" "$OUT"
[[ -f "$OUT" ]] || hrm_die "could not publish the dump to $OUT"
[[ ! -e "$PARTIAL" ]] || hrm_die "unexpected partial file left behind: $PARTIAL"

echo "OK: $OUT ($(du -h "$OUT" | cut -f1), ${OBJECTS} objects, ${TABLES} tables with data, mode $(mode_of "$OUT"))"

# --- retention, only after a verified backup -------------------------------
shopt -s nullglob
ALL_DUMPS=("$BACKUP_DIR"/hrm_prod-*.dump)
shopt -u nullglob
TOTAL="${#ALL_DUMPS[@]}"

mapfile -t EXPIRED < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'hrm_prod-*.dump' \
                        -mtime "+$RETENTION_DAYS" -printf '%T@ %p\n' 2>/dev/null |
                        sort -n | cut -d' ' -f2-)
REMOVED=0
for old in "${EXPIRED[@]:-}"; do
  [[ -n "$old" && "$old" != "$OUT" && -f "$old" ]] || continue
  if [[ $((TOTAL - REMOVED)) -le "$MIN_KEEP" ]]; then
    echo "Retention: keeping the newest $MIN_KEEP backups, not deleting $old"
    continue
  fi
  rm -f -- "$old" && REMOVED=$((REMOVED + 1))
done

echo "Retention: ${REMOVED} removed, $((TOTAL - REMOVED)) kept (window ${RETENTION_DAYS}d, minimum ${MIN_KEEP})"
echo "BACKUP_FILE=$OUT"
