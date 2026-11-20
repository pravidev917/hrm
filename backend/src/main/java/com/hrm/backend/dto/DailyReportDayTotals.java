package com.hrm.backend.dto;

/** Today's daily-report activity for one administrator, counted in the database. */
public record DailyReportDayTotals(
        long reportCount,
        long employeeCount,
        long profilesSubmitted
) {
}
