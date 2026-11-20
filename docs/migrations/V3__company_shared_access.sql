-- V3: shared company (tenant) access for administrator accounts.
--
-- Background
--   Until now every record was reachable only by the administrator who created
--   it (admin_id), so a second administrator account was effectively a separate,
--   empty organisation. Administrators of one company must share the company's
--   employees, clients, attendance, leave, reports, performance, feedback and
--   dashboard figures.
--
-- What this migration does
--   1. Adds a `companies` table.
--   2. Adds `admins.company_id` and puts every existing administrator into one
--      company (the deployment's single existing organisation).
--   3. Leaves every record table untouched: records are NOT copied, moved or
--      duplicated. Scoping changes from "rows whose admin_id = me" to "rows whose
--      admin belongs to my company", which is a query-level change only.
--   4. `admin_id` columns keep their meaning as *creator attribution* / audit.

CREATE TABLE IF NOT EXISTS companies (
    company_id   BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(150) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

COMMENT ON TABLE companies IS 'Tenant: administrators of one company share its records.';

ALTER TABLE admins ADD COLUMN IF NOT EXISTS company_id BIGINT;

DO $$
DECLARE
    default_company_id BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM companies) THEN
        INSERT INTO companies (company_name) VALUES ('Default Company');
    END IF;

    SELECT MIN(company_id) INTO default_company_id FROM companies;

    -- Every administrator that exists today belongs to the one existing company.
    UPDATE admins SET company_id = default_company_id WHERE company_id IS NULL;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'admins' AND column_name = 'company_id'
          AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE admins ALTER COLUMN company_id SET NOT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_admins_company'
    ) THEN
        ALTER TABLE admins
            ADD CONSTRAINT fk_admins_company
            FOREIGN KEY (company_id) REFERENCES companies (company_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_admins_company ON admins (company_id);

-- clients.company_id mirrors the owning administrator's company so that client
-- codes can be unique per company in the database. Kept in sync by a trigger.
ALTER TABLE clients ADD COLUMN IF NOT EXISTS company_id BIGINT;

UPDATE clients c
SET company_id = a.company_id
FROM admins a
WHERE c.admin_id = a.admin_id
  AND c.company_id IS DISTINCT FROM a.company_id;

CREATE INDEX IF NOT EXISTS idx_clients_company ON clients (company_id);

CREATE OR REPLACE FUNCTION sync_client_company() RETURNS trigger AS $$
BEGIN
    SELECT company_id INTO NEW.company_id FROM admins WHERE admin_id = NEW.admin_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clients_company ON clients;
CREATE TRIGGER trg_clients_company
    BEFORE INSERT OR UPDATE OF admin_id ON clients
    FOR EACH ROW EXECUTE FUNCTION sync_client_company();

-- Client codes are unique per company, not per creator: two administrators of the
-- same company must not be able to register the same client code twice.
DROP INDEX IF EXISTS uk_clients_admin_code;
CREATE UNIQUE INDEX IF NOT EXISTS uk_clients_company_code
    ON clients (company_id, lower(client_code))
    WHERE client_code IS NOT NULL;
