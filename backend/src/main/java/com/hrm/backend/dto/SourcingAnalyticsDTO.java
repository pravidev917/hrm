package com.hrm.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Sourcing analytics built only from daily reports.
 *
 * The range always starts at the first daily report the company ever filed, so it
 * grows with the data instead of being pinned to a hard-coded month, and ends today.
 *
 * @param platforms      the five platforms in display order (Naukri, Apna, LinkedIn,
 *                       Careers, Others) - the columns of the table
 * @param platformTotals profiles sourced per platform over the whole range
 * @param employees      one row per employee who filed a report, most productive first
 */
public record SourcingAnalyticsDTO(
        LocalDate fromDate,
        LocalDate toDate,
        long reportCount,
        List<String> platforms,
        List<PlatformTotal> platformTotals,
        List<EmployeeSource> employees) {

    /** @param profiles profiles sourced on this platform across all employees */
    public record PlatformTotal(String platform, long profiles) {
    }

    /**
     * @param platforms profiles sourced by this employee, keyed by platform name
     *                  (every platform key is present, zero when unused)
     */
    public record EmployeeSource(
            Long employeeId,
            String employeeCode,
            String employeeName,
            String designation,
            String department,
            long profilesSourced,
            long profilesSubmitted,
            Map<String, Long> platforms) {
    }
}
