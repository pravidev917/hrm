# HRM API reference

Base URL (local): `http://localhost:8080`
Authentication: JWT stored in the **HttpOnly cookie `HRM_AUTH`** (SameSite=Lax, path `/`, 24 h).
The browser sends it automatically with `withCredentials: true`; JavaScript cannot read it.

Roles: `ADMIN` (owns employees and clients) and `EMPLOYEE` (belongs to one admin).

All errors use the same JSON shape:

```json
{ "message": "Human readable reason", "fieldErrors": { "startDate": "Start date is required" } }
```

`fieldErrors` is only present for validation failures (HTTP 400).

---

## 1. Authentication — `/api/auth` (public except `/me`)

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/auth/admin/register` | `{ adminName, email, password, contactDetails? }` | Only allowed while **no** admin exists (403 afterwards) |
| POST | `/api/auth/admin/login` | `{ email, password }` | Sets `HRM_AUTH` cookie |
| POST | `/api/auth/employee/register` | `{ adminId, registrationCode, employeeName, email, password, role, contact? }` | Creates account with `PENDING` status |
| POST | `/api/auth/employee/login` | `{ email, password }` | Employee must be `APPROVED` (403 otherwise) |
| POST | `/api/auth/logout` | – | Clears the cookie |
| GET | `/api/auth/me` | – | `{ email, authorities: [{ authority: "ROLE_ADMIN" }] }` |

Status codes: `401` bad credentials, `403` inactive/unapproved account or closed registration, `409` email already registered.

---

## 2. Employees — `/api/admin/employees` (ADMIN)

| Method | Path | Purpose | Status |
|---|---|---|---|
| GET | `/api/admin/employees` | Full list (legacy, unpaged) | ✅ existing |
| **GET** | `/api/admin/employees/page` | Paged + searched + sorted | 🆕 **new** |
| GET | `/api/admin/employees/{id}` | One employee | ✅ existing |
| PUT | `/api/admin/employees/{id}` | Update name/role/contact/email | 🔧 **repaired** (safe DTO) |
| PUT | `/api/admin/employees/{id}/approve` | `PENDING → APPROVED` | ✅ existing |
| PUT | `/api/admin/employees/{id}/reject` | `PENDING → REJECTED` | ✅ existing |
| PATCH | `/api/admin/employees/{id}/activate` | → `APPROVED` | ✅ existing |
| PATCH | `/api/admin/employees/{id}/deactivate` | → `INACTIVE` | ✅ existing |

**`GET /employees/page`** query parameters:

| Name | Default | Description |
|---|---|---|
| `search` | – | Case-insensitive match on name, email, designation, phone |
| `status` | – | `PENDING`, `APPROVED`, `INACTIVE`, `REJECTED` |
| `page` | `0` | Zero-based page index |
| `size` | `10` | 1–100 |
| `sortBy` | `createdAt` | `employeeName`, `email`, `role`, `accountStatus`, `createdAt`, `updatedAt` |
| `direction` | `desc` | `asc` / `desc` |

Response: Spring `Page` envelope — `{ content: [...], page, size, totalElements, totalPages, first, last }`.

Employee object (never contains the password):

```json
{
  "employeeId": 12, "employeeName": "Asha Rao", "email": "asha@example.com",
  "role": "Recruiter", "contact": "+91 90000 00000", "accountStatus": "APPROVED",
  "adminId": 1, "adminName": "Priya Sharma",
  "createdAt": "2026-01-04T09:12:00", "updatedAt": "2026-01-06T10:00:00"
}
```

`adminName` is **new** in this response, so the UI can show who manages the employee without an extra call.

**PUT `/employees/{id}` body** (all fields except `employeeName`/`role` optional):

```json
{ "employeeName": "Asha Rao", "role": "Senior Recruiter", "contact": "+91 90000 00000", "email": "asha@example.com" }
```

`password`, `accountStatus` and `admin` are **not** accepted here (previously the entity was bound
directly, which allowed those to be overwritten). Returns `409 / 400` when the email is already in use.

---

## 3. Clients — `/api/admin/clients` (ADMIN)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/clients` | List the admin's clients | 🔧 **repaired** (circular serialisation) |
| POST | `/api/admin/clients` | Create (status forced to `ACTIVE`) |
| GET | `/api/admin/clients/{id}` | One client |
| PUT | `/api/admin/clients/{id}` | Update |
| PATCH | `/api/admin/clients/{id}/activate` | Activate |
| PATCH | `/api/admin/clients/{id}/deactivate` | Deactivate |
| GET | `/api/admin/clients/search?name=` | Search by name |

---

## 4. Employees' own data — `/api/employee` (EMPLOYEE)

| Method | Path | Purpose | Status |
|---|---|---|---|
| GET | `/api/employee/profile` | Own profile (read-only) | ✅ |
| GET | `/api/employee/dashboard` | Attendance/leave/report totals | ✅ |
| **GET** | `/api/employee/clients` | Active clients of own admin | 🆕 **new** |
| GET | `/api/employee/leaves` | Own leave requests | ✅ |
| POST | `/api/employee/leaves` | Apply | ✅ |
| PUT | `/api/employee/leaves/{id}` | Edit while `PENDING` | ✅ |
| PATCH | `/api/employee/leaves/{id}/cancel` | Cancel while `PENDING` | ✅ |

**`GET /api/employee/clients`** → `[{ clientId, clientName, hrName, status }]`
(a list, not a page). Scoped by the employee's own `admin_id`.

There is **no annual entitlement** in this system, so no "remaining balance" is reported.
Adding one is a business decision (quota per type, carry-over, accrual) that needs product input.

---

## 5. Leave — admin review, `/api/admin/leaves` (ADMIN)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/leaves` | All leave of this admin's employees |
| GET | `/api/admin/leaves/{id}` | One request |
| PATCH | `/api/admin/leaves/{id}/review` | `{ status: "APPROVED" \| "REJECTED", remarks }` — remarks required for rejection |

Rules enforced in `LeaveManagementService` (unchanged): future dates only, end ≥ start, half-day must be a
single non-Sunday date, Sundays excluded from the day count, and no overlap with `PENDING`/`APPROVED` leave.

---

## 6. Dashboard

| Method | Path | Role | Status |
|---|---|---|---|
| GET | `/api/employee/dashboard` | EMPLOYEE | ✅ existing |
| **GET** | `/api/admin/dashboard` | ADMIN | 🆕 **new** |

**`GET /api/admin/dashboard`** →

```json
{
  "asOfDate": "2026-02-10", "monthStart": "2026-02-01",
  "people":   { "totalEmployees": 24, "activeEmployees": 21, "pendingEmployees": 2, "inactiveEmployees": 1 },
  "attendanceToday": { "markedToday": 17, "presentToday": 16, "absentToday": 1, "notMarkedToday": 4 },
  "leaves":   { "pending": 3, "approvedThisMonth": 5, "rejectedThisMonth": 1, "requestsThisMonth": 9, "approvedDaysThisMonth": 7.5 },
  "clients":  { "totalClients": 6, "activeClients": 5 },
  "pendingLeaveQueue": [ { "leaveId": 9, "employeeId": 12, "employeeName": "Asha Rao",
                           "startDate": "2026-02-12", "endDate": "2026-02-13",
                           "numberOfDays": 2.0, "type": "CASUAL", "reason": "Family event" } ],
  "pendingEmployees": [ { "employeeId": 30, "employeeName": "Ravi Kumar",
                          "email": "ravi@example.com", "role": "Recruiter" } ]
}
```

---

## 7. Attendance, reports, performance, feedback, follow-ups (all existing)

| Module | Employee | Admin |
|---|---|---|
| Attendance | `GET/POST/PUT /api/employee/attendance[/{id}]`, `GET .../date-range` | `GET/PUT/DELETE /api/admin/attendance[...]` |
| Daily reports | `GET/POST/PUT/DELETE /api/employee/reports` | `GET /api/admin/reports[...]` |
| Weekly reports | `GET/POST/PUT/DELETE /api/employee/weekly-reports` | `GET/PUT/DELETE /api/admin/weekly-reports[...]` |
| Performance | `GET/POST/PUT/DELETE /api/employee/performance` | `GET/POST /api/admin/performance/employees/{id}`, `PUT/DELETE /api/admin/performance/{id}` |
| Feedback | `GET/POST/PUT/DELETE /api/employee/feedback` | same shape under `/api/admin/feedback` |
| Follow-ups | `GET/POST/PUT/DELETE /api/employee/followups` | same shape under `/api/admin/followups` |

Daily-report creation takes the client as a query parameter: `POST /api/employee/reports?clientId=3`.

---

## 8. Error status conventions (repaired)

`GlobalExceptionHandler` previously mapped **every** `RuntimeException` to `400`. It now maps by intent:

| Condition in the service message | Status |
|---|---|
| `Access denied…`, `… does not belong …` | `403` |
| `… not found` | `404` |
| `already registered`, `already exists` | `409` |
| `not approved`, `not active` | `403` |
| other business rule violations | `400` |
| Bean-validation failure (new handler) | `400` + `fieldErrors` |
| Unexpected | `500` (previously `400`) |

---

## 9. Security notes

- Every `/api/admin/**` route requires `ROLE_ADMIN`; every `/api/employee/**` route requires `ROLE_EMPLOYEE`
  (`SecurityConfig`). Ownership is checked again per record in the service layer.
- `JwtAuthenticationFilter` re-reads the account on each request, so deactivating an
  employee or admin blocks an already-issued token immediately.
- CORS allows only `http://localhost:5173` with credentials.
- Secrets (`DB_PASSWORD`, `JWT_SECRET`, `EMPLOYEE_REGISTRATION_CODE`) come from environment variables;
  `application.properties` has no defaults for them, so the app fails fast if they are missing.

---

## 10. Bug found and fixed during live verification

**`GET /api/admin/clients` returned a corrupt, unusable body.**

Observed on the running server (before the fix):

```
HTTP/1.1 200
... 35 KB of JSON ...
{"message":"Could not write JSON: Document nesting depth (501) exceeds the maximum allowed
 (500, from StreamWriteConstraints.getMaxNestingDepth())"}
```

Cause: the controller returned the JPA `Client` entity. `Client` → `admin` → `reviewedLeaves`
→ `approvedBy` → the same `Admin` again, so Jackson recursed until it hit its depth limit and
wrote a truncated body **after** a `200` status. The clients page could not load at all.

Fix: added `ClientResponseDTO` (flat: id, name, HR name/contact, the three counters,
joining details, status) and mapped every `ClientController` method through it.
The same request now returns 209 bytes of valid JSON.

Any other endpoint returning an entity with a back-reference is a candidate for the same
problem; only the client endpoints were affected in practice.
