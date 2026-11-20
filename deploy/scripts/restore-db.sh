#!/usr/bin/env bash
# Restore the HRM database from a custom-format dump.
#
#   sudo deploy/scripts/restore-db.sh /var/backups/hrm/hrm_prod-<stamp>.dump --confirm
#   sudo deploy/scripts/restore-db.sh <dump> --confirm --auto-recover
#   sudo deploy/scripts/restore-db.sh <dump> --confirm --allow-unexpected-objects
#
# Honest description of what is atomic and what is not:
#
#   * the dump is validated BEFORE anything is stopped or changed;
#   * the safety backup of the current database is a separate, verified step;
#   * if the target already holds tables, "DROP SCHEMA public CASCADE; CREATE SCHEMA
#     public;" runs as ONE transaction - that step alone is atomic;
#   * the restore that follows is a SEPARATE transaction. The two steps together
#     are NOT atomic: if the restore fails after the schema was replaced, the
#     database is left with an empty schema. The verified safety dump is the
#     recovery path, and --auto-recover makes the script replay it automatically;
#   * post-checks (object list, core tables, row reads) must pass; a mismatch is a
#     failure, not a warning.
#
# Safety rules enforced here: the target database identity is confirmed, system
# databases are refused, objects in "public" that are not part of the dump are
# refused (they would be destroyed by the schema drop), and credentials live in a
# temporary PGPASSFILE removed on every exit path.
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/pg-env.sh
. "$SCRIPT_DIR/lib/pg-env.sh"

DUMP="${1:-}"
shift || true
CONFIRM=""
AUTO_RECOVER=0
ALLOW_UNEXPECTED=0
for arg in "$@"; do
  case "$arg" in
    --confirm) CONFIRM="--confirm" ;;
    --auto-recover) AUTO_RECOVER=1 ;;
    --allow-unexpected-objects) ALLOW_UNEXPECTED=1 ;;
    *) printf '!! unknown argument: %s\n' "$arg" >&2; exit 2 ;;
  esac
done

ENV_FILE="${HRM_ENV_FILE:-/etc/hrm/backend.env}"
SERVICE="${HRM_SERVICE:-hrm-backend}"
HEALTH_URL="${HRM_HEALTH_URL:-http://127.0.0.1:8080/api/health}"
EXPECTED_TABLES="${HRM_EXPECTED_TABLES:-}"
CORE_TABLES="${HRM_CORE_TABLES:-employees admins}"
SAFETY_LOG=""
SAFETY_DUMP=""
SERVICE_STOPPED=0
SERVICE_RESTARTED=0
SCHEMA_REPLACED=0
RESTORE_OK=0

usage() {
  cat <<'EOF'
usage: restore-db.sh <dumpfile> --confirm [--auto-recover] [--allow-unexpected-objects]

  <dumpfile>                    custom-format dump (pg_dump -Fc)
  --confirm                     mandatory; there is no harmless dry run of a write
  --auto-recover                if the restore fails after the schema was replaced,
                                replay the verified safety dump automatically
  --allow-unexpected-objects    permit replacing a "public" schema that contains
                                objects this dump does not know about

environment overrides: HRM_ENV_FILE HRM_SERVICE HRM_HEALTH_URL HRM_EXPECTED_TABLES
                       HRM_CORE_TABLES HRM_BACKUP_DIR
EOF
}

# Names of objects of one type in schema public, taken from the archive listing.
dump_objects() {
  local file="$1" type="$2"
  pg_restore --list "$file" 2>/dev/null | tr -d '\r' |
    awk -v t="$type" '$4 == t && $5 == "public" { print $6 }' | sort -u
}

# Everything in public that a schema drop would destroy.
live_objects() {
  psql --no-psqlrc -tAc "
    select coalesce(string_agg(c.relname, ' ' order by c.relname), '')
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind in ('r','p','v','m','S','f')" | tr -d '\r'
}

live_tables() {
  psql --no-psqlrc -tAc "
    select coalesce(string_agg(tablename, ' ' order by tablename), '')
    from pg_tables where schemaname = 'public'" | tr -d '\r'
}

if [[ -z "$DUMP" ]]; then
  usage
  exit 2
fi
if [[ "$CONFIRM" != "--confirm" ]]; then
  echo "Refusing to restore without --confirm."
  echo "This replaces the contents of the production database. Inspect the dump first:"
  echo "  pg_restore --list $DUMP | head -40"
  echo
  usage
  exit 1
fi

# --- 1. validate the archive before touching anything ----------------------
[[ -f "$DUMP" ]] || hrm_die "dump not found: $DUMP"
[[ -r "$DUMP" ]] || hrm_die "dump is not readable: $DUMP"
[[ -s "$DUMP" ]] || hrm_die "dump is empty: $DUMP"

hrm_check_tools pg_restore psql
pg_restore --list "$DUMP" >/dev/null 2>&1 \
  || hrm_die "$DUMP is not a readable custom-format dump; refusing to restore"

# newline list for exact per-name greps; space-sorted one-liner for comparison
DUMP_TABLE_NAMES="$(dump_objects "$DUMP" TABLE)"
DUMP_TABLES="$(printf '%s\n' "$DUMP_TABLE_NAMES" | tr '\n' ' ' | sed 's/ *$//')"
DUMP_TABLE_COUNT="$(printf '%s\n' "$DUMP_TABLE_NAMES" | grep -c . || true)"
DUMP_DATA_COUNT="$(pg_restore --list "$DUMP" | grep -c 'TABLE DATA' || true)"
[[ "${DUMP_DATA_COUNT:-0}" -gt 0 ]] || hrm_die "$DUMP contains no TABLE DATA; refusing to restore an empty schema"

missing_core=""
for core in $CORE_TABLES; do
  printf '%s\n' "$DUMP_TABLE_NAMES" | grep -qx "$core" || missing_core="$missing_core $core"
done
[[ -z "$missing_core" ]] || hrm_die "$DUMP does not look like an HRM database (missing tables:$missing_core)"

if [[ -n "$EXPECTED_TABLES" && "$DUMP_TABLE_COUNT" != "$EXPECTED_TABLES" ]]; then
  hrm_die "$DUMP contains $DUMP_TABLE_COUNT tables, HRM_EXPECTED_TABLES=$EXPECTED_TABLES; refusing to restore"
fi
echo "== source dump verified: $DUMP (${DUMP_TABLE_COUNT} tables, ${DUMP_DATA_COUNT} data sections) =="

# --- 2. connection + service handling --------------------------------------
hrm_pg_prepare "$ENV_FILE" pg_restore psql

restart_service_if_stopped() {
  [[ "$SERVICE_STOPPED" -eq 1 && "$SERVICE_RESTARTED" -eq 0 ]] || return 0
  echo "-- starting $SERVICE again (restore is exiting)"
  if systemctl start "$SERVICE"; then
    SERVICE_RESTARTED=1
  else
    echo "!! $SERVICE could not be started; start it manually and check: journalctl -u $SERVICE -n 100 --no-pager" >&2
  fi
}
cleanup_safety_log() {
  [[ -n "${SAFETY_LOG:-}" && -f "${SAFETY_LOG:-}" ]] && rm -f -- "$SAFETY_LOG"
  return 0
}
hrm_cleanup_register restart_service_if_stopped
hrm_cleanup_register cleanup_safety_log

# --- 3. confirm the target identity ---------------------------------------
TARGET_DB="$(psql --no-psqlrc -tAc 'select current_database()' 2>/dev/null | tr -d ' \r\n' || true)"
TARGET_USER="$(psql --no-psqlrc -tAc 'select current_user' 2>/dev/null | tr -d ' \r\n' || true)"
[[ -n "$TARGET_DB" ]] || hrm_die "cannot connect to ${PGDATABASE} at ${PGHOST}:${PGPORT}; nothing was changed"
[[ "$TARGET_DB" == "$PGDATABASE" ]] || hrm_die "connected database '$TARGET_DB' is not '$PGDATABASE'; nothing was changed"
echo "== target confirmed: database '$TARGET_DB' as role '$TARGET_USER' on ${PGHOST}:${PGPORT} =="

EXISTING_TABLES_LIST="$(live_tables | tr '\n' ' ' | sed 's/ *$//')"
EXISTING_TABLES="$(printf '%s\n' "$EXISTING_TABLES_LIST" | tr ' ' '\n' | grep -c . || true)"

# Objects in public that the dump does not contain: a schema drop would delete
# them, so they are a hard stop unless explicitly allowed.
UNEXPECTED=""
for obj in $(live_objects); do
  if ! printf '%s\n' "$DUMP_TABLE_NAMES" | grep -qx "$obj" \
     && ! dump_objects "$DUMP" SEQUENCE | grep -qx "$obj"; then
    UNEXPECTED="$UNEXPECTED $obj"
  fi
done
if [[ -n "$UNEXPECTED" ]]; then
  echo "!! objects exist in schema public that are not part of this dump:$UNEXPECTED" >&2
  if [[ "$ALLOW_UNEXPECTED" -eq 1 ]]; then
    echo "!! continuing because --allow-unexpected-objects was given; they will be DESTROYED" >&2
  else
    hrm_die "refusing to drop a schema containing foreign objects; nothing was changed. \
Inspect them, or re-run with --allow-unexpected-objects if they are expendable."
  fi
fi

echo "== target currently has ${EXISTING_TABLES} public table(s) =="

if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet "$SERVICE"; then
  echo "== stopping $SERVICE =="
  systemctl stop "$SERVICE" || hrm_die "could not stop $SERVICE; nothing was changed"
  SERVICE_STOPPED=1
else
  echo "== $SERVICE is not running (it must stay stopped during the restore) =="
fi

# --- 4. safety backup, verified independently -----------------------------
SAFETY_LOG="$(mktemp "${TMPDIR:-/tmp}/hrm-safety.XXXXXX")"
echo "== safety backup of the current database =="
BACKUP_ALLOW_EMPTY=0
[[ "${EXISTING_TABLES:-0}" -eq 0 ]] && BACKUP_ALLOW_EMPTY=1
if ! HRM_BACKUP_ALLOW_EMPTY="$BACKUP_ALLOW_EMPTY" "$SCRIPT_DIR/backup-db.sh" 2>&1 | tee "$SAFETY_LOG"; then
  hrm_die "safety backup FAILED - restore aborted, the database was not touched"
fi
SAFETY_DUMP="$(grep -m1 '^BACKUP_FILE=' "$SAFETY_LOG" | cut -d= -f2- || true)"
[[ -n "$SAFETY_DUMP" ]] || hrm_die "safety backup did not report a file - restore aborted"
[[ -s "$SAFETY_DUMP" ]] || hrm_die "safety backup is missing or empty ($SAFETY_DUMP) - restore aborted"
pg_restore --list "$SAFETY_DUMP" >/dev/null 2>&1 \
  || hrm_die "safety backup is not readable ($SAFETY_DUMP) - restore aborted"

# The safety dump must describe every table the live database currently has.
if [[ "${EXISTING_TABLES:-0}" -gt 0 ]]; then
  SAFETY_TABLES="$(dump_objects "$SAFETY_DUMP" TABLE)"
  for tbl in $EXISTING_TABLES_LIST; do
    printf '%s\n' "$SAFETY_TABLES" | grep -qx "$tbl" \
      || hrm_die "safety backup does not contain table '$tbl' - restore aborted, the database was not touched"
  done
fi
echo "== safety backup verified: $SAFETY_DUMP (kept; this is the undo path) =="

# --- 5. replace the schema, then restore (two transactions, not atomic) ---
if [[ "${EXISTING_TABLES:-0}" -gt 0 ]]; then
  echo "== replacing schema public (this single step is atomic) =="
  if ! printf 'BEGIN;\nDROP SCHEMA public CASCADE;\nCREATE SCHEMA public;\nCOMMIT;\n' \
       | psql --no-psqlrc -v ON_ERROR_STOP=1 -q >/dev/null; then
    hrm_die "could not replace schema public (insufficient privileges?) - the schema drop was rolled back, nothing was changed. \
Run the restore as a role that owns the database/schema."
  fi
  SCHEMA_REPLACED=1
  echo "!! note: the schema is now EMPTY and the restore below is a separate transaction."
  echo "!!       If it fails, the database stays empty until the safety dump is replayed."
fi

echo "== restoring into $PGDATABASE at $PGHOST:$PGPORT (single transaction) =="
if pg_restore --no-owner --no-privileges --single-transaction --exit-on-error \
              --dbname="$PGDATABASE" "$DUMP"; then
  RESTORE_OK=1
fi

recover_from_safety_dump() {
  echo "== replaying the safety dump: $SAFETY_DUMP =="
  pg_restore --list "$SAFETY_DUMP" >/dev/null 2>&1 || { echo "!! safety dump unreadable" >&2; return 1; }
  printf 'BEGIN;\nDROP SCHEMA public CASCADE;\nCREATE SCHEMA public;\nCOMMIT;\n' \
    | psql --no-psqlrc -v ON_ERROR_STOP=1 -q >/dev/null || { echo "!! could not reset schema public" >&2; return 1; }
  pg_restore --no-owner --no-privileges --single-transaction --exit-on-error \
             --dbname="$PGDATABASE" "$SAFETY_DUMP"
}

if [[ "$RESTORE_OK" -eq 0 ]]; then
  echo "!! restore FAILED. The restore transaction was rolled back." >&2
  if [[ "$SCHEMA_REPLACED" -eq 1 ]]; then
    echo "!! the schema drop was already committed, so the database is currently EMPTY." >&2
    if [[ "$AUTO_RECOVER" -eq 1 ]]; then
      if recover_from_safety_dump; then
        echo "!! automatic recovery succeeded: the previous data is back" >&2
        exit 4
      fi
      echo "!! automatic recovery FAILED - manual action required" >&2
    fi
    echo "!! recover with: sudo $SCRIPT_DIR/restore-db.sh $SAFETY_DUMP --confirm" >&2
  fi
  echo "!! the database was not left in a consistent state; see the notes above" >&2
  exit 4
fi

# --- 6. post-checks: mismatches are failures ------------------------------
echo "== post-checks =="
ACTUAL_TABLES_LIST="$(live_tables | tr '\n' ' ' | sed 's/ *$//')"
ACTUAL_TABLES="$(printf '%s\n' "$ACTUAL_TABLES_LIST" | tr ' ' '\n' | grep -c . || true)"

if [[ "$ACTUAL_TABLES_LIST" != "$DUMP_TABLES" ]]; then
  echo "!! restored tables differ from the dump" >&2
  echo "!!   expected: $DUMP_TABLES" >&2
  echo "!!   actual  : $ACTUAL_TABLES_LIST" >&2
  echo "!! recover with: sudo $SCRIPT_DIR/restore-db.sh $SAFETY_DUMP --confirm" >&2
  exit 5
fi
echo "-- public base tables after restore: $ACTUAL_TABLES (match the dump)"

for core in $CORE_TABLES; do
  rows="$(psql --no-psqlrc -tAc "select count(*) from public.\"$core\"" 2>/dev/null | tr -d ' \r\n' || true)"
  if [[ ! "$rows" =~ ^[0-9]+$ ]]; then
    echo "!! cannot read table '$core' after the restore" >&2
    echo "!! recover with: sudo $SCRIPT_DIR/restore-db.sh $SAFETY_DUMP --confirm" >&2
    exit 5
  fi
  echo "--   $core: $rows row(s)"
done

# --- 7. service restart, reported accurately ------------------------------
RESTART_RC=0
if [[ "$SERVICE_STOPPED" -eq 1 ]]; then
  echo "== starting $SERVICE =="
  if systemctl start "$SERVICE"; then
    SERVICE_RESTARTED=1
  else
    echo "!! systemctl start $SERVICE FAILED - the restore is complete but the service is DOWN" >&2
    RESTART_RC=1
  fi
  if [[ "$RESTART_RC" -eq 0 ]] && command -v curl >/dev/null 2>&1; then
    healthy=0
    for _ in $(seq 1 20); do
      if curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null | grep -q '"UP"'; then healthy=1; break; fi
      sleep 2
    done
    if [[ "$healthy" -eq 1 ]]; then
      echo "-- $SERVICE is healthy on $HEALTH_URL"
    else
      echo "!! $SERVICE started but $HEALTH_URL did not report UP - the restore succeeded, the service is not healthy" >&2
      RESTART_RC=1
    fi
  elif [[ "$RESTART_RC" -eq 0 ]]; then
    echo "-- curl is not installed: the health gate was NOT run (service start succeeded)"
  fi
fi

[[ -f "$SAFETY_DUMP" ]] || echo "!! the safety dump disappeared during the run: $SAFETY_DUMP" >&2

echo
echo "OK: restore finished from $DUMP"
echo "Safety dump kept: $SAFETY_DUMP"
echo "Undo with:  sudo $SCRIPT_DIR/restore-db.sh $SAFETY_DUMP --confirm"
echo "Verify with: curl -fsS $HEALTH_URL"
if [[ "$RESTART_RC" -ne 0 ]]; then
  echo "SERVICE STATUS: not healthy - see the messages above (exit 3)" >&2
  exit 3
fi
exit 0
