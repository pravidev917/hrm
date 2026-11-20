package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The signed-in employee's own performance tracker.
 *
 * Everything is derived from that employee's records only (daily reports of the
 * selected day, weekly reports of the selected month, their own salary), except
 * the comparison board which is scoped to the employee's own organisation and
 * carries no figures beyond resume counts.
 */
public record EmployeeTrackerDTO(
        LocalDate date,
        String month,
        LocalDate monthStart,
        LocalDate monthEnd,
        DailyResumeTarget daily,
        MonthlyRevenueTarget revenue,
        List<ComparisonRow> comparison
) {

    /**
     * @param target    resumes required per day (5)
     * @param achieved  resumes actually submitted on the date
     * @param remaining how many are still required (0 once the target is met)
     * @param percent   0-100, capped for the chart
     * @param met       true when achieved >= target
     */
    public record DailyResumeTarget(
            int target,
            long achieved,
            long remaining,
            int percent,
            boolean met
    ) {
    }

    /**
     * @param salary    the employee's monthly salary from their own record (may be null)
     * @param target    salary x 3 (null when the salary is not set)
     * @param achieved  sum of weekly revenue for the month
     * @param remaining target - achieved, floored at 0
     * @param percent   0-100, capped for the chart
     * @param met       true when achieved >= target
     */
    public record MonthlyRevenueTarget(
            BigDecimal salary,
            BigDecimal target,
            BigDecimal achieved,
            BigDecimal remaining,
            int percent,
            boolean met,
            int weeksCounted
    ) {
    }

    public record ComparisonRow(
            Long employeeId,
            String employeeName,
            long resumes,
            boolean isYou
    ) {
    }
}
