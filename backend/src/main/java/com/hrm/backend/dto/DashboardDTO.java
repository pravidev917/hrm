package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DashboardDTO(
        LocalDate asOfDate,
        EmployeeProfileDTO profile,
        AttendanceSummary attendance,
        LeaveSummary leaves,
        DailyReportSummary dailyReports,
        WeeklyReportSummary weeklyReports) {

    /**
     * Today's attendance for the employee's dashboard.
     *
     * todayStatus keeps the historical vocabulary (PRESENT / ABSENT / NOT_MARKED) so the
     * existing badge keeps working, while the login fields carry what the login/logout
     * model adds: who is logged in right now, since when, and how long today adds up to.
     */
    public record AttendanceSummary(
            String todayStatus,
            long presentThisMonth,
            long absentThisMonth,
            long recordedThisMonth,
            String dayStatus,
            boolean loggedIn,
            Instant firstLoginAt,
            Instant lastLogoutAt,
            long totalWorkSeconds,
            long activeSeconds,
            Instant activeSince,
            int sessionCount,
            boolean wfhApproved,
            boolean onLeave,
            boolean workingDay) {
    }

    public record LeaveSummary(
            long pendingRequests,
            long approvedRequests,
            long rejectedRequests,
            long cancelledRequests,
            BigDecimal totalApprovedDays) {
    }

    public record DailyReportSummary(
            long reportCount,
            long profilesSubmitted,
            long candidatesSourced,
            long candidatesContacted,
            long connectedCount) {
    }

    public record WeeklyReportSummary(
            long reportCount,
            long profilesSubmitted,
            long interviewsScheduled,
            long finalSelections,
            long joined,
            BigDecimal revenueGenerated) {
    }
}
