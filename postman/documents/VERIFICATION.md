# Verification report — live run against PostgreSQL

Date: 2026-11-15
Database: local `postgresql-x64-18`, service on `127.0.0.1:5432`, database `hrm_db`
Backend: Spring Boot on `http://localhost:8080` · Frontend: Vite on `http://localhost:5173`

## How it was run

```bash
# backend (secrets from backend/.env.local, which is git-ignored)
cd backend
set -a && . ./.env.local && set +a
./mvnw spring-boot:run

# frontend
cd frontend
npm run dev
```

`backend/.env.local` (not committed — listed in `.gitignore` as `.env.*`):

```
DB_URL=jdbc:postgresql://localhost:5432/hrm_db
DB_USERNAME=postgres
DB_PASSWORD=<your password>
JWT_SECRET=<at least 32 characters>
EMPLOYEE_REGISTRATION_CODE=<your code>
```

Hibernate started cleanly against the existing schema (`ddl-auto=update`) and printed no
query-validation errors, which means every JPQL/repository method — old and new — is valid
against the real tables.

## What was verified as actually passing

### Authentication and session
| Check | Expected | Result |
|---|---|---|
| Admin login, valid | 200 + `HRM_AUTH` cookie | pass |
| Admin login, wrong password | 401 | pass |
| Employee login, account `PENDING` | 403 "not approved" | pass |
| Second admin self-registration | 403 (registration closed) | pass |
| Employee registration with code | 200, status `PENDING` | pass |
| No cookie on `/api/admin/**` | 401 | pass |
| Garbage cookie / tampered JWT | 401 | pass |
| Employee cookie on `/api/admin/**` | 403 | pass |

### Employee management
| Check | Result |
|---|---|
| `GET /admin/employees/page` paging (`size=5`) | pass — correct `content`/`totalElements`/`totalPages` |
| Search by name (`?search=zeta`) | pass |
| Search with no match | pass — `totalElements: 0` |
| Status filter `PENDING` / `APPROVED` | pass |
| Sort `employeeName desc` | pass |
| Approve an employee | pass — status becomes `APPROVED` |
| Tenant isolation (admin B reading admin A's employee) | **403** (was 400 before the fix) |
| Tenant isolation (admin B approving admin A's employee) | 403 |
| Update with `accountStatus`, `admin`, `password` injected | pass — **all three ignored** |
| Password unchanged after that attack | pass — original password still logs in |
| Update with duplicate email | 400 "Email is already registered" |
| Update with invalid email | 400 + `fieldErrors.email` |
| Update with blank name | 400 + `fieldErrors.employeeName` |

### Leave
| Check | Result |
|---|---|
| Apply leave (future dates) | 201 |
| Past dates | 400 |
| Invalid type | 400 with the allowed list |
| Missing reason | 400 + `fieldErrors.reason` (new) |
| Overlapping request | 400 |
| Half-day on a Sunday | 400 |
| Admin reject with no remarks | 400 |
| Admin approve with remarks | 200; appears in employee history with reviewer name |
| Balance after approval | pass — correct day count, Sunday excluded |

### Dashboard and clients
| Check | Result |
|---|---|
| `GET /admin/dashboard` | pass — real counts, pending queues |
| Same endpoint as a second admin | pass — **all zeros, no cross-tenant leakage** |
| `GET /employee/clients` | pass — only own admin's active clients |
| `GET /employee/dashboard` | pass — reflects the test attendance/leave/report |
| Daily report against a nonexistent client | 404 "Client not found" |
| Daily report persisted | pass — row confirmed directly in PostgreSQL, visible to admin |

### Browser walks (Playwright, real clicks)

Desktop 1440×900:
- Login page: navy brand panel + white form card, role tabs, sub-headline and feature list render correctly.
- Admin login → dashboard: 10 nav links in 5 groups, 4 stat cards aligned, navy sidebar.
- Employees: table with search + status filter, row actions, detail/edit modals.
- Leaves: review actions.
- Clients: loads correctly **(after the fix below)**.
- Daily report form: client dropdown populated from the API; business rules enforced in the form
  ("Connected cannot exceed candidates contacted", "Profiles submitted cannot exceed candidates sourced");
  a valid submission closes the modal, adds a row, and persists.
- Leave balance panel + approval-history timeline render for the employee.

Mobile (Pixel 7 viewport):
- Login: brand panel hidden, form full width.
- Sidebar hidden by default, hamburger opens the drawer.
- Horizontal overflow: **0 px** on dashboard and employees.
- Tables collapse into stacked cards with small uppercase labels; action buttons readable.

### Build and lint
- `npx eslint src` → exit 0.
- `npx vite build` → success, 409 kB JS (126 kB gzip), 27.7 kB CSS.
- `./mvnw -DskipTests compile` → exit 0.

## Bug found during this run and fixed

**`GET /api/admin/clients` was returning a broken response.** It answered `200`, then wrote 35 KB
of JSON and failed mid-stream:

```
{"message":"Could not write JSON: Document nesting depth (501) exceeds the maximum allowed
 (500, from StreamWriteConstraints.getMaxNestingDepth())"}
```

`Client` → `admin` → `reviewedLeaves` → `approvedBy` → the same `Admin`, forever. The clients
page could not load at all. Fixed with `ClientResponseDTO` (flat) mapped in every
`ClientController` method; the response is now 209 bytes of valid JSON. No password was ever
exposed in the broken payload.

## Data hygiene

Every test record I created (two admins, two employees, one client, one leave, one attendance
row, one daily report) was deleted at the end. Post-cleanup counts match the pre-test snapshot
exactly:

| Table | Before | After |
|---|---|---|
| admins | 1 | 1 |
| employees | 1 | 1 |
| clients | 2 | 2 |
| leave_management | 6 | 6 |
| attendance | 2 | 2 |
| daily_reports | 2 | 2 |
| weekly_reports | 1 | 1 |
| feedback | 2 | 2 |

Your accounts `admin@hrm.com` and `rahul@hrm.com`, both clients and all six leave records were
never modified. The `backend/.env.local` file holding the DB password is git-ignored and stays
on this machine only.

## Not verified, and why

- **Your existing `admin@hrm.com` / `rahul@hrm.com` logins were never used.** Both store bcrypt
  hashes I cannot recover, and the app password is different from the database password. All
  authenticated checks therefore ran on throwaway accounts created through the app's own public
  registration endpoint, which exercise exactly the same code paths.
- **Weekly reports, performance, feedback and follow-ups pages** were compiled, linted and opened
  but not clicked through end-to-end in the browser — they still use the older client-side
  `RecordsPage` table rather than the new server-paged table.
- **A real email server** is not configured, so nothing that would send mail was tested.
- **Production build served by a static host** was not tested; only the Vite dev server and
  `vite build` output.

## Next sensible step

Migrate the remaining four record pages (weekly reports, performance, feedback, follow-ups) to the
new `PagedTable` + `Toolbar` pattern so they get server-side paging and the same mobile card
layout as employees and leaves.
