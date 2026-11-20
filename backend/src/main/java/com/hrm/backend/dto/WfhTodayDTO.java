package com.hrm.backend.dto;

import java.time.LocalDate;

/**
 * Today's WFH position for the signed-in employee.
 *
 * {@code date} is the company-local date the server used. The UI compares
 * attendance against it instead of trusting the browser's date near midnight.
 */
public record WfhTodayDTO(
        LocalDate date,
        boolean approved,
        Long wfhId,
        LocalDate startDate,
        LocalDate endDate,
        long pendingCount,
        boolean officeConfigured,
        String officeName,
        Integer radiusMeters
) {
}
