package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Authoritative reporting aggregates for the administrator and employee dashboards.
 *
 * Every figure is computed on the server from stored records. Budget, salary,
 * candidate salary and recognized revenue are deliberately separate fields; a
 * client budget or a sourced profile is never treated as earned revenue.
 */
public record ReportingOverviewDTO(
        LocalDate from,
        LocalDate to,
        String timezone,
        List<String> months,
        boolean partialMonths,
        Filters filters,
        DailyReportsSection dailyReports,
        SourcedProfilesSection sourcedProfiles,
        RevenueTargetSection revenueTargets,
        ClientBudgetSection clientBudgets,
        List<PlatformEffectivenessRow> platformEffectiveness,
        List<PipelineStage> pipeline,
        List<CandidateRow> recentCandidates,
        String revenueBasis,
        String targetBasis,
        String conversionBasis
) {

    public record Filters(
            Long employeeId,
            Long clientId,
            String position,
            String platform
    ) {
    }

    public record DailyReportsSection(
            long eligibleEmployees,
            long submittedReports,
            long sourcedProfiles,
            long missingReports,
            List<DailyReportDay> days,
            String missingBasis
    ) {
    }

    public record DailyReportDay(
            LocalDate date,
            long submittedReports,
            long distinctEmployees,
            long sourcedProfiles,
            long missingReports
    ) {
    }

    public record SourcedProfilesSection(
            long totalSourced,
            List<EmployeeSourceRow> byEmployee,
            List<SourcedPoint> dailyTrend,
            List<PlatformCount> byPlatform,
            long unknownPlatformProfiles,
            long candidatesWithoutDate,
            String basis
    ) {
    }

    public record EmployeeSourceRow(
            Long employeeId,
            String employeeName,
            long sourcedProfiles,
            String source
    ) {
    }

    public record SourcedPoint(LocalDate date, long sourcedProfiles) {
    }

    public record PlatformCount(String platform, long sourcedProfiles) {
    }

    public record RevenueTargetSection(
            BigDecimal totalTarget,
            BigDecimal totalRevenue,
            long employeesWithoutTarget,
            List<EmployeeTargetRow> employees,
            List<String> months,
            boolean partialMonths,
            String basis
    ) {
    }

    public record EmployeeTargetRow(
            Long employeeId,
            String employeeName,
            String role,
            BigDecimal salary,
            BigDecimal target,
            BigDecimal revenue,
            BigDecimal remaining,
            BigDecimal excess,
            BigDecimal achievementPercent,
            BigDecimal rawAchievementPercent,
            boolean targetConfigured,
            int monthsCounted
    ) {
    }

    public record ClientBudgetSection(
            BigDecimal totalBudget,
            BigDecimal totalRevenue,
            BigDecimal totalRemaining,
            BigDecimal unattributedRevenue,
            List<ClientBudgetRow> clients,
            String basis
    ) {
    }

    public record ClientBudgetRow(
            Long clientId,
            String clientCode,
            String clientName,
            String position,
            Integer openings,
            BigDecimal budgetPerCandidate,
            BigDecimal totalBudget,
            BigDecimal actualRevenue,
            BigDecimal remainingBudget,
            BigDecimal coveragePercent,
            boolean budgetConfigured,
            List<ContributionRow> employees
    ) {
    }

    public record ContributionRow(
            Long employeeId,
            String employeeName,
            BigDecimal revenue
    ) {
    }

    public record PlatformEffectivenessRow(
            String platform,
            long sourcedProfiles,
            long outcomeTracked,
            long shortlisted,
            long interviews,
            long offers,
            long joined,
            BigDecimal conversionRate,
            long conversionDenominator,
            BigDecimal attributableRevenue,
            String outcomeSource,
            String note
    ) {
    }

    public record PipelineStage(String stage, long candidates) {
    }

    public record CandidateRow(
            Long feedbackId,
            Long employeeId,
            String employeeName,
            String candidateName,
            String status,
            LocalDate sourcedDate,
            String sourcingPlatform,
            String clientName
    ) {
    }
}
