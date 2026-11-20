# Migrations

Hand-written, versioned SQL. The production database is never touched by
Hibernate `update`.

| File | Purpose |
|------|---------|
| `V1__baseline_schema.sql` | Initial production schema, generated from the JPA entity mappings. Applies only to an empty `public` schema (guarded) — `hrm_prod` is exactly that state today. |
| `V2__clients_notes_and_tracker.sql` | Client master data (client ID, vendor, BDM, position, contact, positions, salary offered, percentage, tax), the optional client link on weekly reports, the `notes` table (employee notes + admin notepad) and the indexes the dashboard/tracker aggregates use. Guarded, so re-running is a no-op. |
| `V3__company_shared_access.sql` | Companies (tenants): `companies` table, `admins.company_id` (existing administrators backfilled into one `Default Company`), `clients.company_id` kept in sync by a trigger so client codes are unique per company. Scoping becomes "company of the caller" — no record table is copied or rewritten. See `docs/COMPANIES.md`. |
| `V4__office_location_and_wfh.sql` | Company office (name, coordinates, radius, timezone), employee WFH requests with review trail, append-only audit log, and attendance work-mode/verification columns. Existing attendance rows keep their status and a `NULL` work mode; no data is rewritten. See `docs/ATTENDANCE-WFH.md`. |
| `V5__reporting_candidate_tracking.sql` | Company sourcing-platform options (seeded defaults), per-platform counts on daily reports, candidate platform/sourced date, client budget per candidate (backfilled from salary × percentage), and month-effective salary history (backfilled from current salary). Additive and idempotent; existing reports, candidates and clients keep their data. See `docs/REPORTING.md`. |

## Workflow

1. Every entity change ships with a new `Vn__description.sql` here.
2. Both the migration and the entity change go into the same release.
3. Apply in staging first; record the outcome in the release notes.
4. Apply to production with the backend stopped, inside the provided
   `BEGIN; … COMMIT;` transaction.
5. Start the backend with `DB_DDL_AUTO=validate`; a mismatch fails the boot
   loudly instead of silently altering the schema.
6. Keep `update` in development only, never in the prod profile.

## Naming and style

- One transaction per file, no `DROP` without an explicit note.
- Constraints named explicitly: `uk_<table>_<columns>`, `fk_<table>_<column>`.
- Migration files are reviewed exactly like code and referenced by the
   `docs/PRODUCTION.md` release checklist.