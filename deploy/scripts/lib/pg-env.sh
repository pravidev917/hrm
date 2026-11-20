#!/usr/bin/env bash
# Shared PostgreSQL connection helpers for the HRM operations scripts.
#
# Sourced, never executed. Provides:
#   hrm_die MESSAGE                 - write to stderr and exit 1
#   hrm_note MESSAGE                - normal progress output
#   hrm_cleanup_register FUNCTION   - run FUNCTION when the script exits
#   hrm_load_db_env FILE            - read only the database keys from the env file
#   hrm_parse_db_url                - JDBC DB_URL -> libpq PGHOST/PGPORT/PGDATABASE
#   hrm_pg_prepare FILE TOOLS...    - the whole setup in one call
#   hrm_pg_setup                    - create a 0600 temporary PGPASSFILE
#   hrm_pg_remove_temp              - delete it again (idempotent)
#
# Two design points that matter for safety:
#
# 1. The application speaks JDBC through DB_URL; pg_dump/pg_restore/psql are libpq
#    clients. The URL is parsed, never modified, and the password is handed over in
#    a temporary PGPASSFILE so it is never an argument, never in `ps`, never logged.
#
# 2. Traps. Exactly one handler is installed here (EXIT, INT, TERM) and it runs a
#    list of cleanups registered by the caller, in reverse order, before the
#    original exit status is restored. Callers must therefore NOT install their own
#    EXIT trap - they call hrm_cleanup_register once. That removes the class of bug
#    where a later `trap ... EXIT` silently replaced the pgpass cleanup (or the
#    other way round) and a password file survived the run.
#
#    Order of a normal run:  hrm_pg_prepare / hrm_pg_setup / ... / exit
#    Order of an interrupted run:  INT|TERM -> exit 130|143 -> EXIT handler ->
#    caller cleanups -> pgpass removal -> exit 130|143.

HRM_CLEANUP_FNS=()
HRM_PG_DIR=""
HRM_PGPASSFILE=""
HRM_TRAPS_INSTALLED=0

hrm_die() {
  printf '!! %s\n' "$*" >&2
  exit 1
}

hrm_note() {
  printf '%s\n' "$*"
}

# --- cleanup registry -------------------------------------------------------
hrm_cleanup_register() {
  local fn="$1"
  declare -F "$fn" >/dev/null 2>&1 || hrm_die "internal: cleanup function '$fn' is not defined"
  HRM_CLEANUP_FNS+=("$fn")
  # installing here means a caller can never register a cleanup and then forget
  # the trap (the old bug this module exists to prevent)
  hrm_install_traps
}

hrm_pg_remove_temp() {
  local dir="${HRM_PG_DIR:-}"
  unset PGPASSFILE HRM_PGPASSFILE
  HRM_PG_DIR=""
  [[ -n "$dir" ]] || return 0
  [[ -d "$dir" ]] || return 0
  case "$dir" in
    "${TMPDIR:-/tmp}"/hrm-pg.*) rm -rf -- "$dir" ;;
    *) printf '!! refusing to remove an unexpected temp path: %s\n' "$dir" >&2 ;;
  esac
}

hrm_run_cleanups() {
  local rc=$?
  trap - EXIT INT TERM
  local i fn
  for ((i = ${#HRM_CLEANUP_FNS[@]} - 1; i >= 0; i--)); do
    fn="${HRM_CLEANUP_FNS[i]}"
    "$fn" || printf '!! cleanup step reported a problem: %s\n' "$fn" >&2
  done
  hrm_pg_remove_temp
  exit "$rc"
}

hrm_install_traps() {
  [[ "$HRM_TRAPS_INSTALLED" -eq 1 ]] && return 0
  HRM_TRAPS_INSTALLED=1
  trap 'hrm_run_cleanups' EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
}

# --- configuration ----------------------------------------------------------
# Read only the keys the operations need. JWT_SECRET, the bootstrap admin
# password and everything else in the env file stay out of this process.
hrm_load_db_env() {
  local file="$1" line key value
  [[ -r "$file" ]] || hrm_die "env file not readable: $file"

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" == *=* ]] || continue
    key="${line%%=*}"
    key="${key//[[:space:]]/}"
    value="${line#*=}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    case "$value" in
      \"*\") value="${value#\"}"; value="${value%\"}" ;;
      \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    case "$key" in
      DB_URL|DB_USERNAME|DB_PASSWORD|PGSSLMODE)
        printf -v "$key" '%s' "$value"
        ;;
    esac
  done < "$file"

  [[ -n "${DB_URL:-}" ]] || hrm_die "DB_URL is not set in $file"
  [[ -n "${DB_USERNAME:-}" ]] || hrm_die "DB_USERNAME is not set in $file"
  return 0
}

# jdbc:postgresql://host:port/database?sslmode=require -> PGHOST PGPORT PGDATABASE
hrm_parse_db_url() {
  local url="${1:-${DB_URL:-}}" rest authority path query hostport pair
  local IFS='&'

  case "$url" in
    jdbc:postgresql://*) rest="${url#jdbc:postgresql://}" ;;
    postgresql://*|postgres://*)
      hrm_die "DB_URL must stay a JDBC URL for Spring Boot (jdbc:postgresql://host:port/db); found a libpq URL"
      ;;
    *) hrm_die "DB_URL is not a JDBC PostgreSQL URL: expected jdbc:postgresql://host:port/database" ;;
  esac

  authority="${rest%%/*}"
  path="${rest#*/}"
  if [[ "$path" == "$rest" || -z "$path" ]]; then
    hrm_die "DB_URL has no database name: expected jdbc:postgresql://host:port/database"
  fi

  query=""
  case "$path" in
    *\?*) query="${path#*\?}"; path="${path%%\?*}" ;;
  esac

  hostport="${authority##*@}"
  if [[ "$hostport" == *:* ]]; then
    PGHOST="${hostport%%:*}"
    PGPORT="${hostport##*:}"
  else
    PGHOST="$hostport"
    PGPORT="5432"
  fi
  [[ -n "$PGHOST" ]] || PGHOST="127.0.0.1"
  [[ "$PGPORT" =~ ^[0-9]+$ ]] || { unset IFS; hrm_die "DB_URL port is not numeric: $PGPORT"; }

  PGDATABASE="$path"
  [[ -n "$PGDATABASE" ]] || { unset IFS; hrm_die "DB_URL has no database name"; }

  if [[ -n "$query" ]]; then
    for pair in $query; do
      case "$pair" in
        sslmode=*) PGSSLMODE="${pair#*=}" ;;
      esac
    done
  fi
  unset IFS

  export PGHOST PGPORT PGDATABASE
  [[ -n "${PGSSLMODE:-}" ]] && export PGSSLMODE
  return 0
}

# libpq pgpass fields are colon separated: escape backslash first, then colon.
hrm_pg_escape_field() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//:/\\:}"
  printf '%s' "$value"
}

hrm_is_loopback_host() {
  case "$1" in
    127.0.0.1|localhost|::1|\[::1\]) return 0 ;;
    *) return 1 ;;
  esac
}

hrm_validate_credentials() {
  local system_dbs=" postgres template0 template1 "

  [[ "$PGDATABASE" != "" ]] || hrm_die "empty database name"
  case "$system_dbs" in
    *" $PGDATABASE "*) hrm_die "refusing to operate on the system database '$PGDATABASE'" ;;
  esac

  if [[ -z "${DB_PASSWORD:-}" ]]; then
    if [[ "${HRM_ALLOW_EMPTY_PASSWORD:-0}" == "1" ]]; then
      hrm_note "-- DB_PASSWORD is empty and HRM_ALLOW_EMPTY_PASSWORD=1: relying on peer/trust authentication"
      return 0
    fi
    hrm_die "DB_PASSWORD is empty for ${DB_USERNAME}@${PGHOST}:${PGPORT}/${PGDATABASE}. \
TCP connections normally use scram-sha-256 and will fail without it; fill DB_PASSWORD in the env file, \
or set HRM_ALLOW_EMPTY_PASSWORD=1 if this host really uses peer/trust authentication."
  fi
  return 0
}

hrm_pg_setup() {
  local dir

  hrm_install_traps
  hrm_pg_remove_temp            # never leak a previous directory in the same process

  dir="$(mktemp -d "${TMPDIR:-/tmp}/hrm-pg.XXXXXX")" || hrm_die "cannot create a temporary directory"
  HRM_PG_DIR="$dir"

  if [[ -n "${DB_PASSWORD:-}" ]]; then
    HRM_PGPASSFILE="$dir/pgpass"
    # Subshell + redirection: the secret is never an argument of any command.
    ( umask 077
      printf '%s:%s:%s:%s:%s\n' \
        "$(hrm_pg_escape_field "$PGHOST")" \
        "$(hrm_pg_escape_field "$PGPORT")" \
        "$(hrm_pg_escape_field "$PGDATABASE")" \
        "$(hrm_pg_escape_field "$PGUSER")" \
        "$(hrm_pg_escape_field "$DB_PASSWORD")" > "$HRM_PGPASSFILE" ) \
      || hrm_die "cannot write the temporary pgpass file"
    chmod 600 "$HRM_PGPASSFILE" 2>/dev/null || true
    export PGPASSFILE="$HRM_PGPASSFILE"
  fi

  unset DB_PASSWORD PGPASSWORD
  return 0
}

hrm_check_tools() {
  local tool
  for tool in "$@"; do
    command -v "$tool" >/dev/null 2>&1 || hrm_die "$tool is not installed (postgresql-client package)"
  done
}

hrm_pg_prepare() {
  local env_file="$1"
  shift
  hrm_install_traps
  hrm_load_db_env "$env_file"
  PGUSER="${DB_USERNAME}"
  export PGUSER
  hrm_parse_db_url "$DB_URL"
  hrm_validate_credentials
  hrm_check_tools "$@"
  hrm_pg_setup
  hrm_note "-- libpq target: ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE} (password via PGPASSFILE, never argv)"
}
