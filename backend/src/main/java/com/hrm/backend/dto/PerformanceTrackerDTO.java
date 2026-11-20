package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Administrator performance tracker for one calendar month.
 *
 * Part 1 is the daily-resume leaderboard (from daily report records).
 * Part 2 is the revenue report (from weekly report records) with the employee's
 * own notes for the month.
 *
 * Month attribution rule (used by every monthly number here): a weekly report
 * belongs to the calendar month that contains its weekStartDate. That is stable
 * for four- and five-week months and cannot count a week twice.
 */
public record PerformanceTrackerDTO(
        String month,
        LocalDate monthStart,
        LocalDate monthEnd,
        int dailyTarget,
        Leaderboard leaderboard,
        RevenueReport revenue
) {

    public record Leaderboard(
            List<LeaderboardRow> rows,
            long totalResumes,
            long totalReports,
            long employeesWithReports
    ) {
    }

    public record LeaderboardRow(
            int rank,
            Long employeeId,
            String employeeName,
            String role,
            long resumesSubmitted,
            long reportsFiled,
            long daysReported,
            double averageResumesPerDay
    ) {
    }

    public record RevenueReport(
            List<EmployeeRevenue> employees,
            BigDecimal totalRevenue,
            int employeesWithRevenue,
            int weeksCounted
    ) {
    }

    public record EmployeeRevenue(
            Long employeeId,
            String employeeName,
            String role,
            BigDecimal salary,
            BigDecimal monthlyRevenue,
            int weeksCounted,
            List<WeekRow> weeks,
            List<NoteRow> notes
    ) {
    }

    public record WeekRow(
            Long weeklyReportId,
            Integer weekNumber,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            BigDecimal revenue,
            Integer profilesSubmitted,
            Integer interviewsScheduled,
            Integer finalSelections,
            Integer joined,
            String clientName
    ) {
    }

    public record NoteRow(
            Long noteId,
            String title,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
