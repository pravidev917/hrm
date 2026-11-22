package com.hrm.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Administrator attendance summary for a date range: what is happening today, the
 * daily / weekly / monthly working-time totals of the range and the same totals per
 * employee.
 *
 * All figures come from the session rows (and the stored attendance rows for legacy
 * days), computed on the server so two administrators looking at the same range see
 * the same numbers.
 */
public record AdminAttendanceSummaryDTO(
        LocalDate from,
        LocalDate to,
        LocalDate today,
        Today todayCounts,
        RangeTotals weekly,
        RangeTotals monthly,
        RangeTotals range,
        List<DailyTotal> daily,
        List<EmployeeTotal> employees) {

    /** Today's snapshot across the active employees of the company. */
    public record Today(
            long activeEmployees,
            long loggedIn,
            long loggedOut,
            long notLoggedIn,
            long incompleteSessions,
            long onLeave,
            int presencePercent,
            long totalWorkSeconds) {
    }

    public record RangeTotals(
            LocalDate from,
            LocalDate to,
            long sessionCount,
            long totalWorkSeconds,
            long daysWorked) {
    }

    public record DailyTotal(
            LocalDate date,
            long sessionCount,
            long totalWorkSeconds,
            long employeesLoggedIn) {
    }

    public record EmployeeTotal(
            Long employeeId,
            String employeeCode,
            String employeeName,
            String designation,
            long daysWorked,
            long sessionCount,
            long totalWorkSeconds,
            long incompleteSessions) {
    }
}
