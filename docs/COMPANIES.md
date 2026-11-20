# Companies (shared administrator access)

Every administrator belongs to exactly one **company**. Administrators of the same
company share that company's records; different companies stay isolated.

## What is shared

| Shared by the company | Stays private to the account |
|---|---|
| Employees (accounts and profiles) | Administrator-owned notes (dashboard notepad) |
| Clients and client dropdowns | Your own password and contact details |
| Attendance | |
| Leave requests and approvals | |
| Daily and weekly reports | |
| Performance, feedback and follow-ups | |
| Holidays and the calendar | |
| Dashboard figures and the monthly performance tracker | |

Records keep their `admin_id` column. It is **creator attribution and audit
history**, not the visibility rule: an employee created by one administrator is
visible to every administrator of that company.

Employee self-service is unchanged: an employee still sees only their own
attendance, leave, reports and performance.

## How the tenant is resolved

The company is read from the authenticated administrator's row
(`admins.company_id`) on every request — from the `HRM_AUTH` cookie subject, never
from a query parameter or request body. Nothing in the API accepts a company or
administrator id as a scope.

* `CompanyScopeService.companyIdOf(adminId)` — the caller's company.
* `CompanyScopeService.manages(adminId, employee)` — company membership check used
  by every write path (employee edit/delete, attendance corrections, leave review,
  reports, feedback, follow-ups, performance).
* Repository queries scope with
  `… .admin.company.companyId = (select a.company.companyId from Admin a where a.adminId = :adminId)`,
  so a missed service check still cannot return another company's rows.

Refusals are deliberately mixed: another company's **employee** returns `403`, while
another company's **administrator or client** returns `404`, so tenant membership
cannot be probed.

## Creating administrators

`POST /api/admin/admins` assigns the new account to the **creator's** company, taken
from the creator's stored row. The payload has no company field, so a caller cannot
place an account in another company. The new administrator can sign in immediately
and sees the same employees, clients, leave, reports and dashboards.

Guards are per company:

* you cannot deactivate or delete your own account (409);
* you cannot remove the last active administrator of your company (409, checked
  under `SELECT … FOR UPDATE` on the company's administrator rows);
* an administrator of another company looks non-existent (404).

## Database

`docs/migrations/V3__company_shared_access.sql`

* `companies` (`company_id`, `company_name`, `created_at`).
* `admins.company_id` (NOT NULL, FK) — existing rows are backfilled into one
  `Default Company`, which is exactly the single organisation that existed before
  this change. No record table is rewritten, copied or duplicated.
* `clients.company_id` mirrors the owning administrator's company via a trigger so
  client codes are unique **per company** (`uk_clients_company_code`) instead of per
  creator.

## Adding a second company later

There is no API for creating a company on purpose: one deployment is one
organisation. To split into a second company, create the row and move the accounts:

```sql
INSERT INTO companies (company_name) VALUES ('Second Company');

UPDATE admins SET company_id = (SELECT company_id FROM companies WHERE company_name = 'Second Company')
WHERE email IN ('person@second.example');

-- clients.company_id follows its administrator through trg_clients_company
```

Records whose creator stayed in the first company (for example an employee account,
because `admin_id` is attribution) remain with that company. Reassign them
deliberately if they belong to the new one; nothing is moved automatically.
