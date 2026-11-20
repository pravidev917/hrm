# Reporting, revenue and candidate tracking

All dashboard figures are computed by the backend in `ReportingService`; the browser only renders them. Both dashboards share the same rules and company timezone.

## Filters

Date range, employee, client, position and sourcing platform. The employee dashboard always forces the signed-in employee as the scope, so an employee cannot request another employee's records or company-wide revenue. Administrators see company data shared by all administrators of that company.

## Metric definitions

- **Revenue**: `SUM(weekly_reports.revenue_generated)` for weeks whose `week_start_date` falls inside the selected range. Each week is counted once. Revenue assigned to a client comes from `weekly_reports.client_id`; revenue without a client is reported separately.
- **Monthly target**: salary effective in each month × 3. Month-effective salaries come from `employee_salary_history`, so changing today's salary does not rewrite historical targets. If any month has no salary, the employee shows **Target not configured**, not a zero target.
- **Achievement**: revenue ÷ summed target. The ring is capped at 100%; the real percentage and any excess are printed next to it.
- **Client position budget**: `number_of_positions × budget_per_candidate`. Client budget is the sum of its position budgets. This is capacity, never revenue.
- **Client revenue**: recognized weekly revenue attributed to that client, with an employee breakdown.
- **Sourced profiles**: candidate records with a `sourced_date` in range are authoritative. Daily-report counts are used only for employees with no candidate records in the range, so one profile is not counted twice. Blank platforms and undated history are shown as **Unknown**.
- **Platform effectiveness**: sourced count per platform; shortlisted, interviews, offers and joins from candidate stages; conversion = joined ÷ candidate records sourced on that platform. Report-only counts have no candidate outcomes, so their conversion is **Not tracked** rather than guessed. Revenue is not attributed per platform.
- **Pipeline**: Sourced, Screened, Submitted, Interview, Offered, Joined, Rejected, Withdrawn, Unknown. Legacy Selected maps to Offered, legacy Dropped maps to Withdrawn, and legacy interview flags remain valid.
- **Missing reports**: weekdays (Mon–Fri) minus days with a report, excluding approved leave. Employees are eligible once they have at least one report in the range. Missing is shown as missing, not as zero productivity.
- **Partial months**: labelled wherever a range does not cover whole months; targets are summed for the months included and never annualised.

## Data changes

`V5__reporting_candidate_tracking.sql` adds:

- `sourcing_platforms` (company-scoped, seeded with LinkedIn, Naukri, Indeed, Referral, Company Website, Other per company).
- `daily_reports.sourcing_platform` and `daily_report_platform_counts` (counts must reconcile with `candidates_sourced`).
- `feedback.sourcing_platform` and `feedback.sourced_date`.
- `clients.budget_per_candidate` (backfilled from `salary_offered × percentage_per_candidate ÷ 100`).
- `employee_salary_history` (one row per employee per effective month, backfilled from the current salary at the employee's creation month).

The migration is additive and guarded. Existing rows keep their values; historical blank platforms and dates remain blank and are labelled Unknown in reports. Administrators can add or disable platform options under **Office settings**; disabling an option does not change existing records.

## Known limitations

- Sourcing-platform revenue is not attributable from the existing weekly-report model, so the platform chart leaves it blank instead of inventing an allocation.
- Daily reports count profiles but do not track per-candidate outcomes; report-only platform rows show conversion as not tracked.
- Missing-report expectations assume Monday–Friday and approved leave only; there is no holiday calendar or part-time schedule.
- Candidate client attribution matches the candidate's client name text to a client record. A free-text mismatch cannot be attributed.
- Weekly revenue is recognised by week start date in the company timezone; a week is never split across two months.
