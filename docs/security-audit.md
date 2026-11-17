# Security audit — HRM (Spring Boot + React)

Scope: backend `com.hrm.backend` (93 Java files, ~9k lines) and the React client.
Method: source review of authentication, authorization, data ownership, error
handling, request validation, configuration and the client's handling of the
session. No live or production system was probed.

Reviewed commit: `8a1e83e` (develop) plus the fixes below.

## Fixed in this pass

| # | Finding | Where | Severity | Fix |
|---|---------|-------|----------|-----|
| 1 | Employee sign-in also returned the JWT in an `X-Auth-Token` response header, so page scripts (and any XSS) could read the session token that the HTTP-only cookie is meant to hide. No client code used the header. | `AuthController.loginEmployee` | medium | Header removed; token now exists only in the `HRM_AUTH` cookie. |
| 2 | The auth cookie was always created with `secure(false)`, so it would also be sent over plain HTTP behind the future TLS terminator. | `AuthController.createAuthCookie` | medium | `COOKIE_SECURE` / `COOKIE_SAME_SITE` properties; production sets `COOKIE_SECURE=true`. Cookie max-age now follows `jwt.expiration` instead of a hardcoded 24 h. |
| 3 | CORS allowed exactly one hardcoded origin (`http://localhost:5173`), which breaks the deployed frontend and tempts a wildcard fix later. | `SecurityConfig.corsConfigurationSource` | low | `CORS_ALLOWED_ORIGINS` property (comma separated, credentials still restricted to explicit origins). |
| 4 | The catch-all `RuntimeException` handler echoed `getMessage()` to the client. JDBC/JPA failures (`DataAccessException`) and NPEs therefore leaked table, constraint and class details. | `GlobalExceptionHandler` | medium | Messages are echoed only when they look like short business messages; `DataAccessException` and anything unexpected return a generic 500 and are logged server-side. `server.error.include-message/stacktrace=false`. |
| 5 | No throttling on either sign-in endpoint: unlimited password guessing against `/api/auth/admin/login` and `/api/auth/employee/login`. | `AuthService` | medium | `LoginAttemptService`: 5 failures per identifier+client address locks that pair for 15 minutes (HTTP 429), cleared on success. |
| 6 | `JWT_SECRET` was accepted at any length; an HS256 key shorter than 256 bits is brute-forceable, which would allow forging admin tokens. | `JwtService` | high (if misconfigured) | Startup validation refuses to boot when the secret is under 32 bytes or the expiry is not positive. |
| 7 | First-run admin seeding accepted an 8-character password and had no weak-value check. | `DefaultAdminInitializer` | medium | Minimum 12 characters, rejected when it equals the email local part or contains `password`/`admin123`/`changeme`/`letmein`; logs a reminder to rotate the seeded password. |
| 8 | Request bodies were unbounded (`identifier`/`password` had no `@Size`), so oversized payloads reached BCrypt hashing. | `LoginRequest`, `EmployeeCreateRequest`, `AdminCreateRequest` | low | `@Size` bounds added (identifier ≤150, password 8–72 characters). |
| 9 | Security headers were default-only; production would send no `X-Content-Type-Options`/`Referrer-Policy`/frame denial. | `SecurityConfig` | low | `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` added. |
| 10 | `show-sql=true` and `ddl-auto=update` were hardcoded, so production logs would contain every statement and schema changes would be applied implicitly. | `application.properties` | low | Both are environment-driven (`JPA_SHOW_SQL`, `DB_DDL_AUTO`) with development-friendly defaults; see `docs/PRODUCTION.md`. |

CSRF stays disabled on purpose: the API is stateless, every state change is a
JSON `fetch` from the app origin, and the cookie is `SameSite=Lax`, so a
cross-site form post cannot carry the session. If the cookie is ever switched to
`SameSite=None` (cross-site embedding), CSRF protection must be turned back on.

## Verified as already sound

- **Authorization boundaries.** `/api/admin/**` requires `ROLE_ADMIN`,
  `/api/employee/**` requires `ROLE_EMPLOYEE`; the JWT filter additionally
  re-checks the account row on every request, so a deactivated employee or
  disabled admin loses access immediately even with a valid token.
- **Data ownership.** Employee endpoints resolve the employee from the
  authenticated email, never from a request parameter. Admin listings are scoped
  by `admin_id` in JPQL (`EmployeeRepository.searchByAdmin`), and cross-tenant
  writes are rejected in the services (`"Access denied"` / `"does not belong"`
  checks in attendance, leave, reports, feedback, follow-up, performance,
  weekly report and client services). Admin feedback/follow-up/performance
  create-update-delete all call the ownership helper before touching a row.
- **Credential handling.** BCrypt hashing, generic "invalid credentials"
  message for unknown user and wrong password alike, inactive-account message
  only after a correct password.
- **No self-registration.** Account creation is admin-only; approval endpoints
  and the registration code are gone.
- **Injection.** All queries are JPA/JPQL with bound parameters; the paged
  employee list uses a sort-property allow list (`SORTABLE`), so `sortBy` cannot
  be abused.

## Residual risks (accepted or requiring infrastructure)

1. **Session revocation.** A token stays valid for 24 h (`jwt.expiration`); the
   filter re-checks account status, so deactivation is effective immediately, but
   a stolen token cannot be revoked individually. A token version column or
   short-lived token + refresh would close this. Lower `JWT_EXPIRATION_MS` if
   that matters more than session length.
2. **Throttle storage is in-process.** A multi-instance deployment needs a shared
   counter (Redis) or a gateway limit; also add an nginx `limit_req` for the two
   login paths.
3. **Password policy is length-only** (8+). Consider a breached-password check or
   a minimum of 12 for admin accounts.
4. **No MFA** for administrator accounts.
5. **TLS, HSTS and proxy trust live outside this repository** — they must be
   configured on the nginx/ALB layer (see `docs/PRODUCTION.md`).
6. **Schema management** is still Hibernate `update` by default; a versioned
   migration tool (Flyway/Liquibase) is the durable fix. Until then, production
   should set `DB_DDL_AUTO=validate` after a manual baseline.
