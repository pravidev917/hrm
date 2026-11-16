package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Leave position for the signed-in employee.
 *
 * IMPORTANT: this project has no stored leave-entitlement policy (there is no annual
 * quota column or configuration), so no allowance is invented here. The response reports
 * only what the database actually knows: days taken, days requested and what is pending.
 * A per-type breakdown is derived from the same records.
 */
public record LeaveBalanceDTO(
        LocalDate asOfDate,
        LocalDate yearStart,
        LocalDate yearEnd,
        Totals totals,
        List<TypeBreakdown> byType,
        List<HistoryEntry> history
) {

    public record Totals(
            BigDecimal approvedDaysThisYear,
            BigDecimal pendingDaysThisYear,
            BigDecimal approvedDaysAllTime,
            long approvedRequestsThisYear,
            long pendingRequests,
            long rejectedRequests,
            long cancelledRequests
    ) {
    }

    public record TypeBreakdown(
            String type,
            BigDecimal approvedDaysThisYear,
            BigDecimal pendingDaysThisYear,
            long approvedRequestsThisYear
    ) {
    }

    /** One reviewed (or cancelled) request, newest first — the approval history. */
    public record HistoryEntry(
            Long leaveId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal numberOfDays,
            String status,
            String adminName,
            String adminRemarks,
            java.time.LocalDateTime reviewedAt
    ) {
    }
}
