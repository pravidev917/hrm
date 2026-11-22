-- V7: an employee may file more than one daily report per day.
--
-- The "one report per employee per day" rule lived only in the application
-- (DailyReportService.validateDuplicateReport) and has been removed, so there is no
-- constraint to drop on a clean database. The statements below are defensive: they
-- remove a uniqueness rule if an older environment created one by hand, and they add the
-- index that keeps "all reports for this employee and day" fast now that several rows can
-- share a date.
--
-- Every consumer already aggregates rows, so several reports add up instead of replacing
-- each other:
--   * admin / employee dashboards     - COUNT and SUM over the date range
--   * reporting + performance tracker - SUM per employee per day
--   * sourcing analytics              - SUM per employee and per platform
--   * admin report list               - one row per report, all of them

BEGIN;

-- No unique (employee_id, report_date) rule may remain.
ALTER TABLE public.daily_reports
    DROP CONSTRAINT IF EXISTS daily_reports_employee_id_report_date_key;

ALTER TABLE public.daily_reports
    DROP CONSTRAINT IF EXISTS uq_daily_reports_employee_date;

DROP INDEX IF EXISTS public.ux_daily_reports_employee_date;
DROP INDEX IF EXISTS public.daily_reports_employee_id_report_date_key;

-- Lookups by employee and day now mean "find all of them", so index the pair.
CREATE INDEX IF NOT EXISTS ix_daily_reports_employee_date
    ON public.daily_reports (employee_id, report_date);

COMMIT;
