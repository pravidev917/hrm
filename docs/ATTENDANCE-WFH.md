# Office attendance and work from home

Location-based office attendance and an administrator-approved work-from-home
(WFH) workflow.

## Configure the office (administrator)

1. Sign in as an administrator.
2. Open **Time & leave → Office settings** (`/office-settings`).
3. Enter the office name, latitude and longitude, the allowed radius in metres
   and the attendance timezone (an IANA zone such as `Asia/Kolkata`).
   - **Use my current location** fills latitude/longitude from the browser.
   - Manual coordinates work too.
4. Save. The settings are stored per company, so every current and future
   administrator of the same company reads and edits the same row. Every change
   is written to `audit_logs`.

Only `ADMIN` accounts can reach the settings endpoints. The company is always
derived from the authenticated administrator, never from the request body.

The radius is deliberately configurable (10–10000 m). An exact GPS match is not
required; attendance is accepted anywhere inside the radius.

## Request and approve WFH

Employee:

1. Open **Attendance → Request WFH**, or **Work from home → Request WFH**.
2. Choose a start date, end date and reason.
3. Follow the status (Pending, Approved, Rejected, Cancelled) in **Work from
   home**. Pending requests can be cancelled.

Administrator:

1. Open **Time & leave → WFH requests** (`/wfh`).
2. Review a pending request and approve or reject it with an optional comment.
3. The request shows who decided and when.
4. An approved request can be revoked. Revocation does not rewrite attendance
   already recorded; the change is audited.

Rules enforced by the backend:

- past dates and `endDate < startDate` are rejected;
- active requests (Pending or Approved) may not overlap;
- a pending or approved leave request on the same dates blocks a WFH request;
- only an administrator of the employee's company may review;
- an administrator cannot approve a request whose employee email matches their
  own (self-approval);
- only Pending requests can be reviewed or cancelled by the employee;
- only Approved requests can be revoked;
- an expired pending request cannot be approved.

Decision notifications: this project has no separate notification service. The
employee sees the decision, reviewer and timestamp in their WFH history, and the
sidebar shows the pending count. Toast feedback confirms each action.

## Mark attendance

**Mark present** does two different things, decided entirely on the server:

- If an **Approved** WFH request covers the company-local today, present is
  recorded as `Present — WFH` without asking for a location.
- Otherwise the browser requests a fresh high-accuracy position, and the server
  checks that it is within the configured radius and has acceptable accuracy.

Pending, rejected, cancelled, expired and future WFH requests never unlock the
WFH path. WFH approval does **not** mark attendance automatically; the employee
still clicks **Mark present**.

**Mark absent** remains available without location verification.

### Verification thresholds

| Setting | Default | Meaning |
|---|---|---|
| `ATTENDANCE_MAX_ACCURACY_METERS` | `50` | Browser fixes with worse accuracy are rejected. |
| `LOCATION_MAX_AGE_SECONDS` | `120` | A capture older than this is rejected as stale. |
| Office radius | per company | Verified great-circle distance must be inside it. |
| `APP_TIMEZONE` | `Asia/Kolkata` | Fallback when the company has not set an office timezone. |

The server:

- resolves the employee from the authenticated session;
- resolves the office and company from the employee row;
- uses the office timezone (fallback `APP_TIMEZONE`) for the attendance date;
- computes the distance itself (haversine) from the stored office coordinates;
- stores only distance, accuracy, the capture time and the work mode — never the
  employee's precise latitude/longitude;
- enforces one record per employee per date with a database unique constraint
  and a pessimistic employee lock;
- writes attendance and WFH changes to the audit trail.

### Error messages and UI states

The attendance page shows:

- `Checking location…` while the browser fix is requested and `Marking present…`
  while the request is submitted;
- clear messages for denied permission, timeout, unavailable location, poor
  accuracy, stale readings and a missing office configuration;
- `Present — Office` and `Present — WFH` in the employee's today card, calendar
  detail and admin attendance table;
- an approved-WFH banner for today and the WFH request history inline, with the
  full history at `/wfh`.

## Privacy and limitations

- Location is requested only when an employee marks office attendance. There is
  no continuous tracking, no background collection and no home coordinates for
  WFH.
- Stored location evidence is limited to distance/accuracy and is visible only to
  the employee themselves and administrators of the same company.
- Browser geolocation requires a **secure context**: HTTPS, or `localhost` for
  development. Plain HTTP on a LAN address will fail.
- **Browser-provided location can be spoofed.** This feature is a policy and
  proximity aid, not proof of physical presence, and must not be described as
  spoof-proof.
- Administrative attendance corrections are privileged and audited. They record
  an explicit work mode; WFH can only be selected when an approved request
  covers the date.

## API summary

| Method | Path | Role |
|---|---|---|
| GET | `/api/admin/office-location` | ADMIN |
| PUT | `/api/admin/office-location` | ADMIN |
| GET | `/api/employee/office-location` | EMPLOYEE |
| POST | `/api/employee/wfh` | EMPLOYEE |
| GET | `/api/employee/wfh` | EMPLOYEE |
| GET | `/api/employee/wfh/today` | EMPLOYEE |
| PATCH | `/api/employee/wfh/{id}/cancel` | EMPLOYEE |
| GET | `/api/admin/wfh` | ADMIN |
| PATCH | `/api/admin/wfh/{id}/review` | ADMIN |
| PATCH | `/api/admin/wfh/{id}/revoke` | ADMIN |
| POST | `/api/employee/attendance?status=PRESENT` | EMPLOYEE, body = location evidence |
| PUT | `/api/employee/attendance/{id}?status=...` | EMPLOYEE, body = location evidence when changing to PRESENT |

## Applying the migration

The Docker database applies `V1..V4` only when its volume is empty. On an
existing volume, apply V4 by hand before starting the new backend:

```bash
docker compose exec -T db psql -U postgres -d hrm_db -v ON_ERROR_STOP=1 \
  < docs/migrations/V4__office_location_and_wfh.sql
docker compose up -d --build backend web
```

Never delete the `db-data` volume to apply a migration.
