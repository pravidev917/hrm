package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Review history for the signed-in employee: requests an administrator has
 * already decided on (approved/rejected) plus anything the employee cancelled.
 * Pending requests are not history and are returned by the leave list instead.
 */
public record LeaveHistoryDTO(
        List<HistoryEntry> history
) {

    /** One reviewed (or cancelled) request, newest first. */
    public record HistoryEntry(
            Long leaveId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal numberOfDays,
            String status,
            String adminName,
            String adminRemarks,
            LocalDateTime reviewedAt
    ) {
    }
}