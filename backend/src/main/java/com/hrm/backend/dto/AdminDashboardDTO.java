package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin dashboard aggregates. Every number is computed from stored records —
 * there are no estimates or decorative metrics.
 */
public record AdminDashboardDTO(
        LocalDate asOfDate,
        LocalDate monthStart,
        PeopleSummary people,
        AttendanceSummary attendanceToday,
        LeaveSummary leaves,
        ClientSummary clients,
        List<PendingLeave> pendingLeaveQueue,
        List<PendingEmployee> pendingEmployees
) {

    public record PeopleSummary(
            long totalEmployees,
            long activeEmployees,
            long pendingEmployees,
            long inactiveEmployees
    ) {
    }

    public record AttendanceSummary(
            long markedToday,
            long presentToday,
            long absentToday,
            long notMarkedToday
    ) {
    }

    public record LeaveSummary(
            long pending,
            long approvedThisMonth,
            long rejectedThisMonth,
            long requestsThisMonth,
            BigDecimal approvedDaysThisMonth
    ) {
    }

    public record ClientSummary(
            long totalClients,
            long activeClients
    ) {
    }

    /** One row of the review queue: who, when, how long. */
    public record PendingLeave(
            Long leaveId,
            Long employeeId,
            String employeeName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal numberOfDays,
            String type,
            String reason
    ) {
    }

    public record PendingEmployee(
            Long employeeId,
            String employeeName,
            String email,
            String role
    ) {
    }
}
