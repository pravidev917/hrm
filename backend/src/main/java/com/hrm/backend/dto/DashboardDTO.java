package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardDTO(
        LocalDate asOfDate,
        EmployeeProfileDTO profile,
        AttendanceSummary attendance,
        LeaveSummary leaves,
        DailyReportSummary dailyReports,
        WeeklyReportSummary weeklyReports) {

    public record AttendanceSummary(
            String todayStatus,
            long presentThisMonth,
            long absentThisMonth,
            long recordedThisMonth) {
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
