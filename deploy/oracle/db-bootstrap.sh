#!/usr/bin/env bash
# Apply the HRM schema (V1..V5) to a fresh Supabase PostgreSQL database.
#
# Run once, before the first backend start, from any machine that can reach the
# database - your laptop or the Oracle VM:
#
#   DB_HOST=aws-0-ap-south-1.pooler.supabase.com \
#   DB_PORT=5432 DB_NAME=postgres DB_USER=postgres.<project-ref> \
#   DB_PASSWORD='<supabase-db-password>' \
#   ./deploy/oracle/db-bootstrap.sh
#
# Supabase details:
#   * prefer the SESSION pooler on port 5432 (IPv4). The direct host
#     db.<ref>.supabase.co is IPv6-only on new projects and also needs TLS;
#   * transaction mode (port 6543) supports neither DDL nor prepared statements,
#     so it cannot run these migrations;
#   * TLS is mandatory - DB_SSLMODE defaults to require.
#
# Security notes:
#   * the password is never passed on the command line - it goes into a 0600
#     temporary PGPASSFILE that is removed on exit;
#   * the baseline script itself refuses to run against a non-empty schema, so a
#     second run is a no-op failure rather than a half-applied schema.
#
# Then start the backend with DB_DDL_AUTO=validate (the prod profile default).

set -Eeuo pipefail

DB_HOST="${DB_HOST:?set DB_HOST (Supabase pooler host)}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-postgres}"
DB_USER="${DB_USER:?set DB_USER (postgres.<project-ref> on the pooler)}"
DB_PASSWORD="${DB_PASSWORD:?set DB_PASSWORD}"
DB_SSLMODE="${DB_SSLMODE:-require}"
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
# Supabase refuses plaintext connections; require is enough (no client cert).
export PGSSLMODE="$DB_SSLMODE"

log "checking connectivity to ${DB_HOST}:${DB_PORT}/${DB_NAME}"
psql -v ON_ERROR_STOP=1 -Atc 'SELECT current_database();' >/dev/null || die "cannot connect (check the pooler host, user and password; an IPv6-only direct host fails here)"

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
log "next: point DB_URL at the same host (add ?sslmode=require), set"
log "      DEFAULT_ADMIN_EMAIL / DEFAULT_ADMIN_PASSWORD, then start the backend"
