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
        String month,
        PeopleSummary people,
        AttendanceSummary attendanceToday,
        LeaveSummary leaves,
        ClientSummary clients,
        DailyReportSummary dailyReports,
        MonthlyPerformanceSummary monthlyPerformance,
        List<PendingLeave> pendingLeaveQueue,
        List<RecentEmployee> recentEmployees
) {

    public record PeopleSummary(
            long totalEmployees,
            long activeEmployees,
            long pendingEmployees,
            long inactiveEmployees
    ) {
    }

    /**
     * @param lateTracked    the attendance model stores PRESENT/ABSENT only, so a
     *                       "late" figure would be invented; the UI shows that it
     *                       is not tracked instead of a fake zero
     * @param presencePercent present / active employees, 0 when there are none
     */
    public record AttendanceSummary(
            long markedToday,
            long presentToday,
            long absentToday,
            long notMarkedToday,
            boolean lateTracked,
            int presencePercent
    ) {
    }

    /**
     * Leave details: the all-time totals the dashboard card shows plus the
     * current-month figures it already used.
     */
    public record LeaveSummary(
            long pending,
            long totalRequests,
            long approvedRequests,
            long rejectedRequests,
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

    /** Today's daily report activity, plus the running month totals. */
    public record DailyReportSummary(
            LocalDate date,
            long reportsToday,
            long employeesSubmittedToday,
            long employeesMissingToday,
            long resumesToday,
            long reportsThisMonth,
            long resumesThisMonth
    ) {
    }

    /**
     * Monthly revenue card. {@code rows} is the employee-wise summary for the
     * selected month (weekly revenue summed per employee).
     */
    public record MonthlyPerformanceSummary(
            String month,
            BigDecimal totalRevenue,
            long employeesWithRevenue,
            int weeksCounted,
            String topEmployeeName,
            BigDecimal topEmployeeRevenue,
            List<MonthlyRevenueRow> rows
    ) {
    }

    public record MonthlyRevenueRow(
            Long employeeId,
            String employeeName,
            String role,
            BigDecimal revenue,
            int weeksCounted
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

    /** Newest additions to the team, shown on the dashboard. */
    public record RecentEmployee(
            Long employeeId,
            String employeeCode,
            String employeeName,
            String email,
            String role,
            String department,
            String accountStatus
    ) {
    }
}
