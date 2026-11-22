package com.hrm.backend.service;

import com.hrm.backend.dto.SourcingAnalyticsDTO;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.entity.DailyReportPlatformCount;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.DailyReportRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Profiles sourced" analytics, derived from daily reports only.
 *
 * The same daily report is the single source of truth for the admin dashboard table,
 * the per-employee chart and the per-platform chart, so the three can never disagree.
 * Every figure is a sum of {@code candidatesSourced}, split over the report's platform
 * rows; reports filed before per-platform counts existed fall back to their single
 * platform name.
 *
 * The range starts at the first daily report the company ever filed and ends today -
 * no hard-coded start date.
 */
@Service
public class SourcingAnalyticsService {

    private final DailyReportRepository dailyReportRepository;
    private final AdminService adminService;
    private final CompanyTimeService companyTime;

    public SourcingAnalyticsService(
            DailyReportRepository dailyReportRepository,
            AdminService adminService,
            CompanyTimeService companyTime) {
        this.dailyReportRepository = dailyReportRepository;
        this.adminService = adminService;
        this.companyTime = companyTime;
    }

    @Transactional(readOnly = true)
    public SourcingAnalyticsDTO getAnalytics(String authenticatedEmail) {

        Long adminId = adminService.getAdminIdByEmail(authenticatedEmail);
        Long companyId = adminService.requireCompanyId(adminId);

        LocalDate today = companyTime.todayForCompany(companyId);
        LocalDate from = dailyReportRepository.findFirstReportDateInCompanyOf(adminId)
                .orElse(today);

        if (from.isAfter(today)) {
            from = today;
        }

        List<DailyReport> reports =
                dailyReportRepository.findForAnalyticsInCompanyOf(adminId, from, today);

        Map<String, Long> platformTotals = emptyPlatformMap();
        Map<Long, EmployeeAccumulator> byEmployee = new LinkedHashMap<>();
        for (String platform : SourcingPlatforms.CANONICAL) {
            platformTotals.put(platform, 0L);
        }

        for (DailyReport report : reports) {

            Employee employee = report.getEmployee();
            if (employee == null) {
                continue;
            }

            EmployeeAccumulator row = byEmployee.computeIfAbsent(
                    employee.getEmployeeId(), id -> new EmployeeAccumulator(employee));

            row.profilesSubmitted += nz(report.getProfilesSubmitted());

            Map<String, Long> perPlatform = platformSplit(report);
            long sourcedOnReport = perPlatform.values().stream().mapToLong(Long::longValue).sum();
            row.profilesSourced += sourcedOnReport;

            perPlatform.forEach((platform, count) -> {
                row.platforms.merge(platform, count, Long::sum);
                platformTotals.merge(platform, count, Long::sum);
            });
        }

        List<SourcingAnalyticsDTO.EmployeeSource> employees = new ArrayList<>();
        for (EmployeeAccumulator row : byEmployee.values()) {
            employees.add(row.toDTO());
        }
        employees.sort(Comparator
                .comparingLong(SourcingAnalyticsDTO.EmployeeSource::profilesSourced).reversed()
                .thenComparing(SourcingAnalyticsDTO.EmployeeSource::employeeName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        List<SourcingAnalyticsDTO.PlatformTotal> platformTotalRows = new ArrayList<>();
        for (String platform : SourcingPlatforms.CANONICAL) {
            platformTotalRows.add(new SourcingAnalyticsDTO.PlatformTotal(
                    platform, platformTotals.getOrDefault(platform, 0L)));
        }

        return new SourcingAnalyticsDTO(
                from, today, reports.size(),
                SourcingPlatforms.CANONICAL, platformTotalRows, employees);
    }

    /**
     * Profiles sourced on one report, split by platform.
     *
     * Per-platform rows win because they are what the employee actually entered. When a
     * report only has the legacy single platform field, its whole sourced count is
     * attributed to that platform; unknown names land in "Others" so no profile is lost.
     */
    private Map<String, Long> platformSplit(DailyReport report) {

        Map<String, Long> split = new LinkedHashMap<>();
        List<DailyReportPlatformCount> rows = report.getPlatformCounts();

        if (rows != null) {
            for (DailyReportPlatformCount row : rows) {
                long count = nz(row.getProfileCount());
                if (count <= 0) {
                    continue;
                }
                String platform = SourcingPlatforms.canonicalOrOthers(row.getPlatform());
                split.merge(platform, count, Long::sum);
            }
        }

        long total = split.values().stream().mapToLong(Long::longValue).sum();
        long sourced = nz(report.getCandidatesSourced());

        if (total == 0 && sourced > 0) {
            split.merge(SourcingPlatforms.canonicalOrOthers(report.getSourcingPlatform()),
                    sourced, Long::sum);
        }

        return split;
    }

    private Map<String, Long> emptyPlatformMap() {
        Map<String, Long> map = new LinkedHashMap<>();
        for (String platform : SourcingPlatforms.CANONICAL) {
            map.put(platform, 0L);
        }
        return map;
    }

    private static long nz(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    /** Mutable per-employee total while the reports are being summed. */
    private static final class EmployeeAccumulator {

        private final Employee employee;
        private final Map<String, Long> platforms;
        private long profilesSourced;
        private long profilesSubmitted;

        private EmployeeAccumulator(Employee employee) {
            this.employee = employee;
            this.platforms = new LinkedHashMap<>();
            for (String platform : SourcingPlatforms.CANONICAL) {
                this.platforms.put(platform, 0L);
            }
        }

        private SourcingAnalyticsDTO.EmployeeSource toDTO() {
            return new SourcingAnalyticsDTO.EmployeeSource(
                    employee.getEmployeeId(),
                    employee.getEmployeeCode(),
                    employee.getEmployeeName(),
                    employee.getRole(),
                    employee.getDepartment(),
                    profilesSourced,
                    profilesSubmitted,
                    platforms);
        }
    }
}
