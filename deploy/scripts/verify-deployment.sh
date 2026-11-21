#!/usr/bin/env bash
# Post-deployment checks for a running HRM site.
#
#   deploy/scripts/verify-deployment.sh https://example.com
#   HRM_VERIFY_ADMIN_IDENTIFIER=...    HRM_VERIFY_ADMIN_PASSWORD=... \
#   HRM_VERIFY_EMPLOYEE_IDENTIFIER=... HRM_VERIFY_EMPLOYEE_PASSWORD=... \
#     deploy/scripts/verify-deployment.sh https://example.com
#
# Credential safety:
#   * sign-in checks are role-specific: administrators authenticate on
#     /api/auth/admin/login, employees on /api/auth/employee/login, each with its
#     own variables; one account is never reused for the other role;
#   * credentials are only ever sent to an https:// base URL, with one exception:
#     a loopback host (127.0.0.1 / localhost / [::1]) is allowed over plain HTTP so
#     local tests can run; anything else over http:// is refused;
#   * credentials and session cookies are never printed, logged or echoed, and the
#     wrong-password probe uses a fresh random identifier every run so repeated
#     checks cannot build up a lockout against a real account;
#   * the JSON body is escaped properly, so identifiers or passwords containing
#     quotes, backslashes or control characters survive intact.
#
# Exit codes: 0 all executed checks passed; 1 at least one failed; 2 usage/tooling.
# Checks that cannot run (missing tools, non-HTTPS) are counted as "unavailable",
# separately from failures, and printed as SKIP/UNAVAIL.
set -uo pipefail

BASE="${1:-}"
if [[ -z "$BASE" ]]; then
  echo "usage: verify-deployment.sh <base-url>" >&2
  exit 2
fi
BASE="${BASE%/}"

FAIL=0
SKIP=0

say() { printf '%s\n' "$*"; }
check() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    printf 'PASS  %-44s %s\n' "$label" "$actual"
  else
    printf 'FAIL  %-44s expected %s, got %s\n' "$label" "$expected" "$actual"
    FAIL=$((FAIL + 1))
  fi
}
check_one_of() {
  local label="$1" expected="$2" allowed="$3" actual="$4"
  if [[ " $allowed " == *" $actual "* ]]; then
    printf 'PASS  %-44s %s\n' "$label" "$actual"
  else
    printf 'FAIL  %-44s expected one of [%s], got %s\n' "$label" "$allowed" "$actual"
    FAIL=$((FAIL + 1))
  fi
}
skip() {
  printf 'SKIP  %-44s %s\n' "$1" "$2"
  SKIP=$((SKIP + 1))
}

# --- tools ------------------------------------------------------------------
if ! command -v curl >/dev/null 2>&1; then
  echo "UNAVAILABLE: curl is not installed - no check can run" >&2
  exit 2
fi
json_string() {   # escape for a JSON string literal
  local s="$1" c i out=""
  for ((i = 0; i < ${#s}; i++)); do
    c="${s:i:1}"
    case "$c" in
      '\\') out+='\\' ;;
      '"')  out+='\"' ;;
      $'\n') out+='\n' ;;
      $'\r') out+='\r' ;;
      $'\t') out+='\t' ;;
      *)    out+="$c" ;;
    esac
  done
  printf '%s' "$out"
}

# --- transport policy --------------------------------------------------------
SCHEME="${BASE%%://*}"
REST="${BASE#*://}"
HOST="${REST%%/*}"
HOST="${HOST%%:*}"
HOST="$(printf '%s' "$HOST" | tr -d '[]')"
is_loopback() {
  case "$1" in
    127.0.0.1|localhost|::1) return 0 ;;
    *) return 1 ;;
  esac
}
if [[ "$SCHEME" == "http" ]] && ! is_loopback "$HOST"; then
  echo "REFUSED: refusing to send credentials over plain HTTP to non-loopback host '$HOST'." >&2
  echo "         Use https://, or test only against 127.0.0.1/localhost/[::1]." >&2
  exit 2
fi
if [[ "$SCHEME" != "http" && "$SCHEME" != "https" ]]; then
  echo "REFUSED: base URL scheme must be http:// or https:// (got: $SCHEME)" >&2
  exit 2
fi

code() { curl -s -o /dev/null -m 10 -w '%{http_code}' "$1"; }
json_status() { curl -s -m 10 "$1" | grep -o '"status":"[A-Z]*"' | cut -d'"' -f4; }

# Session login that never leaks: the cookie jar is created by the caller (so the
# EXIT trap owns it) and is passed in by path. Credentials never appear in argv.
JAR_FILES=()
cleanup_jars() {
  local j
  for j in "${JAR_FILES[@]:-}"; do [[ -n "$j" && -e "$j" ]] && rm -f -- "$j"; done
  return 0
}
new_jar() {   # sets HRM_JAR_CURRENT and registers it in THIS shell (no subshell)
  HRM_JAR_CURRENT="$(mktemp)" || { echo "cannot create a temporary cookie jar" >&2; exit 2; }
  JAR_FILES+=("$HRM_JAR_CURRENT")
}
trap 'cleanup_jars' EXIT

# Sign in; prints "<http_code> <cookie_count>". JSON is escaped and sent on stdin.
login() {
  local path="$1" identifier="$2" password="$3" jar="$4" body http_code cookies
  body="$(printf '{"identifier":"%s","password":"%s"}' "$(json_string "$identifier")" "$(json_string "$password")")"
  http_code="$(printf '%s' "$body" | curl -s -m 15 -c "$jar" -o /dev/null -w '%{http_code}' \
      -X POST "$BASE$path" -H 'Content-Type: application/json' --data-binary @-)"
  cookies="$(grep -ci 'HRM_AUTH' "$jar" 2>/dev/null || echo 0)"
  printf '%s %s' "$http_code" "$cookies"
}
auth_as() { curl -s -o /dev/null -m 10 -b "$1" -w '%{http_code}' "$BASE$2"; }

say "== HRM deployment verification: $BASE =="

check "health endpoint" 200 "$(code "$BASE/api/health")"
check "health reports UP" "UP" "$(json_status "$BASE/api/health")"
check "SPA index" 200 "$(code "$BASE/")"
check "SPA deep route" 200 "$(code "$BASE/leaves")"
check "anonymous employee API is 401" 401 "$(code "$BASE/api/employee/profile")"
check "anonymous admin API is 401" 401 "$(code "$BASE/api/admin/employees")"
check "unknown API path does not 500" 401 "$(code "$BASE/api/does-not-exist")"

if [[ "$SCHEME" == "https" ]]; then
  check "HSTS header present" "yes" \
    "$(curl -sI -m 10 "$BASE/" | grep -qi 'strict-transport-security' && echo yes || echo no)"
  check_one_of "HTTP redirects to HTTPS" "301" "301 308" "$(code "http://${BASE#https://}/")"
else
  skip "HSTS header present" "not run on http:// (only meaningful over https)"
  skip "HTTP redirects to HTTPS" "not run on http:// (only meaningful over https)"
fi

HEADERS="$(curl -sI -m 10 "$BASE/" 2>/dev/null)"
check "X-Content-Type-Options" "yes" \
  "$(grep -qi 'x-content-type-options: nosniff' <<<"$HEADERS" && echo yes || echo no)"
check "X-Frame-Options" "yes" \
  "$(grep -qi 'x-frame-options: DENY' <<<"$HEADERS" && echo yes || echo no)"

# --- role-specific sign-in checks -------------------------------------------
ADMIN_ID="${HRM_VERIFY_ADMIN_IDENTIFIER:-}"
ADMIN_PW="${HRM_VERIFY_ADMIN_PASSWORD:-}"
EMP_ID="${HRM_VERIFY_EMPLOYEE_IDENTIFIER:-}"
EMP_PW="${HRM_VERIFY_EMPLOYEE_PASSWORD:-}"

if [[ -n "$ADMIN_ID" && -n "$ADMIN_PW" ]]; then
  say "-- administrator sign-in (HRM_VERIFY_ADMIN_*)"
  A_JAR=""
  new_jar; A_JAR="$HRM_JAR_CURRENT"
  read -r A_CODE A_COOKIE <<<"$(login /api/auth/admin/login "$ADMIN_ID" "$ADMIN_PW" "$A_JAR")"
  check "POST /api/auth/admin/login" 200 "$A_CODE"
  check "admin session cookie set" "1" "$A_COOKIE"
  if [[ "$A_CODE" == "200" ]]; then
    check "admin session reaches admin API" 200 "$(auth_as "$A_JAR" /api/admin/admins)"
    check "admin session is denied the employee API" 403 "$(auth_as "$A_JAR" /api/employee/profile)"
  fi
  read -r W_CODE W_UNUSED <<<"$(login /api/auth/admin/login "nobody-$RANDOM-$(date +%s)@invalid" "wrong-password-$RANDOM" /dev/null)"
  check "wrong admin password is 401" 401 "$W_CODE"
else
  skip "POST /api/auth/admin/login" "HRM_VERIFY_ADMIN_IDENTIFIER / HRM_VERIFY_ADMIN_PASSWORD not set"
fi

if [[ -n "$EMP_ID" && -n "$EMP_PW" ]]; then
  say "-- employee sign-in (HRM_VERIFY_EMPLOYEE_*)"
  E_JAR=""
  new_jar; E_JAR="$HRM_JAR_CURRENT"
  read -r E_CODE E_COOKIE <<<"$(login /api/auth/employee/login "$EMP_ID" "$EMP_PW" "$E_JAR")"
  check "POST /api/auth/employee/login" 200 "$E_CODE"
  check "employee session cookie set" "1" "$E_COOKIE"
  if [[ "$E_CODE" == "200" ]]; then
    check "employee session reaches own profile" 200 "$(auth_as "$E_JAR" /api/employee/profile)"
    check "employee session is denied admin API" 403 "$(auth_as "$E_JAR" /api/admin/admins)"
  fi
  read -r W_CODE W_UNUSED <<<"$(login /api/auth/employee/login "nobody-$RANDOM-$(date +%s)@invalid" "wrong-password-$RANDOM" /dev/null)"
  check "wrong employee password is 401" 401 "$W_CODE"
else
  skip "POST /api/auth/employee/login" "HRM_VERIFY_EMPLOYEE_IDENTIFIER / HRM_VERIFY_EMPLOYEE_PASSWORD not set"
fi

echo
if [[ "$FAIL" -gt 0 ]]; then
  echo "RESULT: $FAIL check(s) FAILED, $SKIP skipped/unavailable"
  exit 1
fi
if [[ "$SKIP" -gt 0 ]]; then
  echo "RESULT: all executed checks passed ($SKIP skipped/unavailable)"
  exit 0
fi
echo "RESULT: all checks passed"
exit 0