# Migrations

Hand-written, versioned SQL. The production database is never touched by
Hibernate `update`.

| File | Purpose |
|------|---------|
| `V1__baseline_schema.sql` | Initial production schema, generated from the JPA entity mappings. Applies only to an empty `public` schema (guarded) — `hrm_prod` is exactly that state today. |

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