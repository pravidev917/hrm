-- V6: canonical sourcing platforms, client compensation type, weekly-report
-- reference columns.
--
-- What changes and why:
--   * Sourcing platforms become a fixed set of five: Naukri, Apna, LinkedIn,
--     Careers, Others. "Indeed" is retired and its rows are folded into "Apna";
--     the free-text leftovers (Referral, Company Website, Other) are folded into
--     "Others" so every report, chart and validation uses the same names.
--   * Clients gain a compensation type (per candidate or salary based) plus the
--     matching value, replacing the old "budget per candidate" figure in the form.
--   * Weekly reports gain the reference columns the admin table shows
--     (candidate name, position, interview status). Existing columns stay.
--
-- The script is written to be safe on a database that already holds data:
-- every fold merges counts into the canonical row instead of dropping them.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Canonical sourcing platforms per company
-- ---------------------------------------------------------------------------

-- Retire "Indeed" in favour of "Apna": rename when no Apna row exists yet,
-- otherwise drop the Indeed row and keep Apna.
UPDATE public.sourcing_platforms p
SET name = 'Apna', updated_at = now()
WHERE lower(p.name) = 'indeed'
  AND NOT EXISTS (
      SELECT 1 FROM public.sourcing_platforms a
      WHERE a.company_id = p.company_id AND lower(a.name) = 'apna'
  );

DELETE FROM public.sourcing_platforms p
WHERE lower(p.name) = 'indeed';

-- Drop the platforms that are no longer offered as their own option.
DELETE FROM public.sourcing_platforms p
WHERE lower(p.name) IN ('referral', 'company website', 'other');

-- Make sure all five exist for every company, in display order.
INSERT INTO public.sourcing_platforms
    (company_id, name, active, sort_order, created_at, updated_at)
SELECT c.company_id, d.name, true, d.sort_order, now(), now()
FROM public.companies c
CROSS JOIN (
    VALUES
        ('Naukri', 10),
        ('Apna', 20),
        ('LinkedIn', 30),
        ('Careers', 40),
        ('Others', 50)
) AS d(name, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM public.sourcing_platforms p
    WHERE p.company_id = c.company_id AND lower(p.name) = lower(d.name)
);

-- Put the five in the display order every form, table and chart expects, including
-- rows that already existed (V5 seeded LinkedIn first).
UPDATE public.sourcing_platforms p
SET sort_order = d.sort_order, updated_at = now()
FROM (
    VALUES
        ('Naukri', 10),
        ('Apna', 20),
        ('LinkedIn', 30),
        ('Careers', 40),
        ('Others', 50)
) AS d(name, sort_order)
WHERE lower(p.name) = lower(d.name)
  AND p.sort_order IS DISTINCT FROM d.sort_order;

-- ---------------------------------------------------------------------------
-- 2. Fold legacy platform names inside existing reports
-- ---------------------------------------------------------------------------

-- 2a. Add the legacy count onto an already-existing canonical row.
UPDATE public.daily_report_platform_counts tgt
SET profile_count = tgt.profile_count + src.profile_count
FROM public.daily_report_platform_counts src
WHERE src.daily_report_id = tgt.daily_report_id
  AND lower(src.platform) IN ('indeed', 'referral', 'company website', 'other')
  AND lower(tgt.platform) = CASE
        WHEN lower(src.platform) = 'indeed' THEN 'apna'
        ELSE 'others'
      END;

-- 2b. Insert a canonical row for reports that do not have one yet.
INSERT INTO public.daily_report_platform_counts
    (daily_report_id, platform, profile_count)
SELECT src.daily_report_id,
       CASE WHEN lower(src.platform) = 'indeed' THEN 'Apna' ELSE 'Others' END,
       src.profile_count
FROM public.daily_report_platform_counts src
WHERE lower(src.platform) IN ('indeed', 'referral', 'company website', 'other')
  AND NOT EXISTS (
      SELECT 1 FROM public.daily_report_platform_counts tgt
      WHERE tgt.daily_report_id = src.daily_report_id
        AND lower(tgt.platform) = CASE
              WHEN lower(src.platform) = 'indeed' THEN 'apna'
              ELSE 'others'
            END
  );

-- 2c. Remove the legacy rows now that their counts live on the canonical name.
DELETE FROM public.daily_report_platform_counts
WHERE lower(platform) IN ('indeed', 'referral', 'company website', 'other');

-- The single-platform fallback column and the candidate tracker use the same names.
UPDATE public.daily_reports
SET sourcing_platform = 'Apna'
WHERE lower(sourcing_platform) = 'indeed';

UPDATE public.daily_reports
SET sourcing_platform = 'Others'
WHERE lower(sourcing_platform) IN ('referral', 'company website', 'other');

UPDATE public.feedback
SET sourcing_platform = 'Apna'
WHERE lower(sourcing_platform) = 'indeed';

UPDATE public.feedback
SET sourcing_platform = 'Others'
WHERE lower(sourcing_platform) IN ('referral', 'company website', 'other');

-- ---------------------------------------------------------------------------
-- 3. Client compensation type
-- ---------------------------------------------------------------------------
-- OFFERED_PER_CANDIDATE: a fixed amount paid per candidate placed.
-- SALARY_BASED:          the annual salary the position carries.

ALTER TABLE public.clients
    ADD COLUMN IF NOT EXISTS compensation_type VARCHAR(30) NOT NULL DEFAULT 'OFFERED_PER_CANDIDATE';

ALTER TABLE public.clients
    ADD COLUMN IF NOT EXISTS compensation_value NUMERIC(12, 2);

-- Keep the number shown in the form for clients created before this change.
UPDATE public.clients
SET compensation_value = COALESCE(budget_per_candidate, salary_offered)
WHERE compensation_value IS NULL;

ALTER TABLE public.clients
    DROP CONSTRAINT IF EXISTS clients_compensation_type_check;

ALTER TABLE public.clients
    ADD CONSTRAINT clients_compensation_type_check
    CHECK (compensation_type IN ('OFFERED_PER_CANDIDATE', 'SALARY_BASED'));

-- ---------------------------------------------------------------------------
-- 4. Weekly report reference columns
-- ---------------------------------------------------------------------------
-- All nullable: weeks filed before this change simply have no candidate/position.

ALTER TABLE public.weekly_reports
    ADD COLUMN IF NOT EXISTS candidate_name VARCHAR(150);

ALTER TABLE public.weekly_reports
    ADD COLUMN IF NOT EXISTS position VARCHAR(150);

ALTER TABLE public.weekly_reports
    ADD COLUMN IF NOT EXISTS interview_status VARCHAR(40);

COMMIT;
