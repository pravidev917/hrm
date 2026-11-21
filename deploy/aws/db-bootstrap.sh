#!/usr/bin/env bash
# Apply the HRM baseline schema to a fresh Amazon RDS PostgreSQL instance.
#
# Run from the EC2 host (or any machine that can reach RDS) once, before the
# first backend start:
#
#   DB_HOST=hrm.xxxxx.ap-south-1.rds.amazonaws.com \
#   DB_PORT=5432 DB_NAME=hrm DB_USER=hrm_admin DB_PASSWORD='...' \
#   ./deploy/aws/db-bootstrap.sh
#
# Security notes:
#   * the password is never passed on the command line - it goes into a 0600
#     temporary PGPASSFILE that is removed on exit;
#   * the baseline script itself refuses to run against a non-empty schema, so a
#     second run is a no-op failure rather than a half-applied schema.
#
# Then start the backend with DB_DDL_AUTO=validate (the prod profile default).

set -Eeuo pipefail

DB_HOST="${DB_HOST:?set DB_HOST (RDS endpoint)}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:?set DB_NAME}"
DB_USER="${DB_USER:?set DB_USER}"
DB_PASSWORD="${DB_PASSWORD:?set DB_PASSWORD}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-$(cd "$(dirname "$0")/../.." && pwd)/docs/migrations}"

# Either one explicit file (BASELINE_SQL) or every V*.sql in the migrations
# directory, applied in version order - a fresh database needs the baseline plus
# all later migrations, otherwise DB_DDL_AUTO=validate rejects the schema.
if [ -n "${BASELINE_SQL:-}" ]; then
  migrations=("$BASELINE_SQL")
else
  shopt -s nullglob
  migrations=("$MIGRATIONS_DIR"/V*.sql)
  shopt -u nullglob
fi

if [[ ${#migrations[@]} -gt 1 ]] && command -v sort >/dev/null; then
  mapfile -t migrations < <(printf '%s\n' "${migrations[@]}" | sort -V)
fi

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
log() { printf '[db-bootstrap] %s\n' "$*"; }

command -v psql >/dev/null || die "psql not found (dnf install postgresql16)"
[[ ${#migrations[@]} -gt 0 ]] || die "no V*.sql migrations found in $MIGRATIONS_DIR"
for m in "${migrations[@]}"; do
  [ -f "$m" ] || die "migration not found: $m"
done
[ -n "$DB_PASSWORD" ] || die "DB_PASSWORD must not be empty"

passfile="$(mktemp "${TMPDIR:-/tmp}/hrm-dbboot.XXXXXX")"
cleanup() { rm -f "$passfile"; }
trap cleanup EXIT INT TERM

( umask 077 && printf '%s:%s:%s:%s:%s\n' "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER" "$DB_PASSWORD" >"$passfile" )
chmod 600 "$passfile"

export PGPASSFILE="$passfile"
export PGHOST="$DB_HOST" PGPORT="$DB_PORT" PGDATABASE="$DB_NAME" PGUSER="$DB_USER"
export PGCONNECT_TIMEOUT=10

log "checking connectivity to ${DB_HOST}:${DB_PORT}/${DB_NAME}"
psql -v ON_ERROR_STOP=1 -Atc 'SELECT current_database();' >/dev/null || die "cannot connect (check the RDS security group and password)"

existing="$(psql -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type IN ('BASE TABLE','VIEW');")"
if [ "$existing" != "0" ]; then
  die "public schema already has ${existing} object(s) - refusing to apply the baseline"
fi

for m in "${migrations[@]}"; do
  log "applying $(basename "$m")"
  psql -v ON_ERROR_STOP=1 -q -f "$m" || die "$(basename "$m") failed - public schema left as it was"
done

tables="$(psql -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';")"
log "done - ${#migrations[@]} migration(s), ${tables} tables in ${DB_NAME}.public"
log "next: set DEFAULT_ADMIN_EMAIL / DEFAULT_ADMIN_PASSWORD in /etc/hrm/backend.env for the first start"
