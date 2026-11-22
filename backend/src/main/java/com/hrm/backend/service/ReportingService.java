package com.hrm.backend.service;

import com.hrm.backend.dto.ReportingOverviewDTO;
import com.hrm.backend.dto.ReportingOverviewDTO.*;
import com.hrm.backend.entity.Client;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeSalaryHistory;
import com.hrm.backend.entity.Feedback;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.entity.WeeklyReport;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.EmployeeSalaryHistoryRepository;
import com.hrm.backend.repository.FeedbackRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.WeeklyReportRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Server-side reporting aggregates.
 *
 * Revenue is the existing definition: SUM(weekly_reports.revenue_generated) for
 * weeks whose weekStartDate falls inside the selected range. Targets are
 * salary x 3 using the salary effective in each month. Client budgets are
 * openings x budget_per_candidate and are never added to revenue.
 */
@Service
public class ReportingService {

    private static final BigDecimal THREE = BigDecimal.valueOf(3);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_RANGE_DAYS = 366;
    private static final String UNKNOWN = "Unknown";

    private final EmployeeRepository employeeRepository;
    private final ClientRepository clientRepository;
    private final DailyReportRepository dailyReportRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final FeedbackRepository feedbackRepository;
    private final LeaveManagementRepository leaveRepository;
    private final EmployeeSalaryHistoryRepository salaryHistoryRepository;
    private final CompanyScopeService companyScope;
    private final CompanyTimeService companyTime;

    public ReportingService(
            EmployeeRepository employeeRepository,
            ClientRepository clientRepository,
            DailyReportRepository dailyReportRepository,
            WeeklyReportRepository weeklyReportRepository,
            FeedbackRepository feedbackRepository,
            LeaveManagementRepository leaveRepository,
            EmployeeSalaryHistoryRepository salaryHistoryRepository,
            CompanyScopeService companyScope,
            CompanyTimeService companyTime) {
        this.employeeRepository = employeeRepository;
        this.clientRepository = clientRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.weeklyReportRepository = weeklyReportRepository;
        this.feedbackRepository = feedbackRepository;
        this.leaveRepository = leaveRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.companyScope = companyScope;
        this.companyTime = companyTime;
    }

    @Transactional(readOnly = true)
    public ReportingOverviewDTO forAdmin(
            Long adminId, String from, String to,
            Long employeeId, Long clientId, String position, String platform) {
        return build(adminId, null, from, to, employeeId, clientId, position, platform);
    }

    @Transactional(readOnly = true)
    public ReportingOverviewDTO forEmployee(
            Long employeeId, String from, String to,
            Long clientId, String position, String platform) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found"));
        if (employee.getAdmin() == null || employee.getAdmin().getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return build(employee.getAdmin().getAdminId(), employeeId,
                from, to, employeeId, clientId, position, platform);
    }

    private ReportingOverviewDTO build(
            Long adminId, Long scopeEmployeeId, String fromText, String toText,
            Long employeeId, Long clientId, String position, String platform) {

        Long companyId = companyScope.companyIdOf(adminId);
        ZoneId zone = companyTime.zoneForCompany(companyId);
        LocalDate today = LocalDate.now(zone);

        LocalDate from = parseDate(fromText, today.withDayOfMonth(1));
        LocalDate to = parseDate(toText, today);
        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Start date must be on or before end date");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a range of at most " + MAX_RANGE_DAYS + " days");
        }

        String positionFilter = trimToNull(position);
        String platformFilter = trimToNull(platform);

        List<Employee> employees = employeeRepository.findByCompanyOf(adminId).stream()
                .filter(e -> scopeEmployeeId == null || scopeEmployeeId.equals(e.getEmployeeId()))
                .filter(e -> employeeId == null || employeeId.equals(e.getEmployeeId()))
                .toList();

        if (employeeId != null && employees.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Employee is not part of this company");
        }

        List<Client> clients = clientRepository.findByCompanyOf(adminId).stream()
                .filter(c -> clientId == null || clientId.equals(c.getClientId()))
                .filter(c -> positionFilter == null
                        || (c.getPosition() != null
                        && c.getPosition().toLowerCase(Locale.ROOT)
                                .contains(positionFilter.toLowerCase(Locale.ROOT))))
                .toList();

        Set<Long> clientIds = new HashSet<>();
        clients.forEach(c -> clientIds.add(c.getClientId()));
        Set<String> clientNames = new HashSet<>();
        clients.forEach(c -> {
            if (c.getClientName() != null) {
                clientNames.add(c.getClientName().trim().toLowerCase(Locale.ROOT));
            }
        });

        Predicate<Employee> employeeFilter = e ->
                e != null && clients != null && employees.stream()
                        .anyMatch(x -> Objects.equals(x.getEmployeeId(), e.getEmployeeId()));

        LocalDate effectiveFrom = from;
        LocalDate effectiveTo = to;

        List<DailyReport> reports = dailyReportRepository
                .findByEmployeeInCompanyOfAndReportDateBetween(adminId, effectiveFrom, effectiveTo)
                .stream()
                .filter(r -> employeeFilter.test(r.getEmployee()))
                .filter(r -> clientId == null || (r.getClient() != null
                        && clientId.equals(r.getClient().getClientId())))
                .filter(r -> positionFilter == null || (r.getClient() != null
                        && r.getClient().getPosition() != null
                        && r.getClient().getPosition().toLowerCase(Locale.ROOT)
                        .contains(positionFilter.toLowerCase(Locale.ROOT))))
                .filter(r -> platformFilter == null
                        || reportSourcedFor(r, platformFilter) > 0)
                .toList();

        List<WeeklyReport> weeks = weeklyReportRepository
                .findByEmployeeInCompanyOfAndWeekStartDateBetween(adminId, effectiveFrom, effectiveTo)
                .stream()
                .filter(w -> employeeFilter.test(w.getEmployee()))
                .filter(w -> clientId == null || (w.getClient() != null
                        && clientId.equals(w.getClient().getClientId())))
                .filter(w -> positionFilter == null || (w.getClient() != null
                        && w.getClient().getPosition() != null
                        && w.getClient().getPosition().toLowerCase(Locale.ROOT)
                        .contains(positionFilter.toLowerCase(Locale.ROOT))))
                .toList();

        List<Feedback> candidates = feedbackRepository.findByEmployeeInCompanyOf(adminId).stream()
                .filter(f -> employeeFilter.test(f.getEmployee()))
                .filter(f -> sourcedInRange(f, effectiveFrom, effectiveTo))
                .filter(f -> matchesCandidateClient(f, clientId, clientNames, positionFilter))
                .toList();

        List<Feedback> candidatesAnyPlatform = candidates;
        List<Feedback> platformCandidates = platformFilter == null
                ? candidates
                : candidates.stream()
                        .filter(f -> platformLabel(f.getSourcingPlatform())
                                .equalsIgnoreCase(platformFilter))
                        .toList();

        List<Feedback> candidatesWithoutDate = feedbackRepository
                .findByEmployeeInCompanyOf(adminId).stream()
                .filter(f -> employeeFilter.test(f.getEmployee()))
                .filter(f -> f.getSourcedDate() == null)
                .filter(f -> matchesCandidateClient(f, clientId, clientNames, positionFilter))
                .filter(f -> platformFilter == null || platformLabel(f.getSourcingPlatform())
                        .equalsIgnoreCase(platformFilter))
                .toList();

        Set<Long> candidateEmployeesAny = new HashSet<>();
        candidatesAnyPlatform.forEach(f -> {
            if (f.getEmployee() != null) {
                candidateEmployeesAny.add(f.getEmployee().getEmployeeId());
            }
        });

        List<LeaveManagement> leaves = leaveRepository
                .findByEmployeeInCompanyOfAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        adminId, effectiveTo, effectiveFrom)
                .stream()
                .filter(l -> "APPROVED".equalsIgnoreCase(l.getStatus()))
                .filter(l -> employeeFilter.test(l.getEmployee()))
                .toList();

        Map<Long, List<EmployeeSalaryHistory>> salaryHistory = new HashMap<>();
        salaryHistoryRepository.findCompanyHistoryUpTo(adminId, effectiveTo.withDayOfMonth(1))
                .forEach(h -> {
                    if (h.getEmployee() != null) {
                        salaryHistory.computeIfAbsent(
                                h.getEmployee().getEmployeeId(), k -> new ArrayList<>()).add(h);
                    }
                });

        DailyReportsSection dailySection = buildDailyReports(
                employees, reports, leaves, effectiveFrom, effectiveTo, platformFilter);
        SourcedProfilesSection sourcedSection = buildSourcedProfiles(
                employees, reports, candidates, platformCandidates,
                candidateEmployeesAny, candidatesWithoutDate.size(),
                effectiveFrom, effectiveTo, platformFilter);
        RevenueTargetSection targetSection = buildTargets(
                employees, weeks, salaryHistory, effectiveFrom, effectiveTo);
        ClientBudgetSection budgetSection = buildBudgets(
                adminId, scopeEmployeeId, clients, weeks, reports, candidatesAnyPlatform,
                effectiveFrom, effectiveTo);
        List<PlatformEffectivenessRow> platformRows = buildPlatformRows(
                reports, platformCandidates, candidatesAnyPlatform,
                candidateEmployeesAny, platformFilter);
        List<PipelineStage> pipeline = buildPipeline(candidatesAnyPlatform);
        List<CandidateRow> recent = platformCandidates.stream()
                .sorted(Comparator
                        .comparing(Feedback::getSourcedDate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Feedback::getFeedbackId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(this::toCandidateRow)
                .toList();

        ReportingOverviewDTO.Filters filters = new ReportingOverviewDTO.Filters(
                scopeEmployeeId != null ? scopeEmployeeId : employeeId,
                clientId, positionFilter, platformFilter);

        return new ReportingOverviewDTO(
                effectiveFrom, effectiveTo, zone.getId(),
                monthsBetween(effectiveFrom, effectiveTo),
                partialMonths(effectiveFrom, effectiveTo),
                filters,
                dailySection,
                sourcedSection,
                targetSection,
                budgetSection,
                platformRows,
                pipeline,
                recent,
                "Revenue is recognised from weekly reports whose week start date falls inside the range; each week is counted once.",
                "Monthly target = salary effective in that month x 3. A month with no salary configured is reported as Target not configured instead of zero.",
                "Conversion = joined / candidates sourced on that platform (candidate records). Daily reports have counts but no candidate outcomes, so their conversion is shown as not tracked.");
    }

    // ----------------------------------------------------------------- daily

    private DailyReportsSection buildDailyReports(
            List<Employee> employees,
            List<DailyReport> reports,
            List<LeaveManagement> leaves,
            LocalDate from, LocalDate to,
            String platformFilter) {

        Set<Long> reporterIds = new HashSet<>();
        reports.forEach(r -> {
            if (r.getEmployee() != null) reporterIds.add(r.getEmployee().getEmployeeId());
        });

        Set<Long> expectedIds = new HashSet<>(reporterIds);
        if (employees.size() == 1 && reports.isEmpty()) {
            expectedIds.add(employees.get(0).getEmployeeId());
        }
        long eligible = expectedIds.size();

        List<DailyReportDay> days = new ArrayList<>();
        long missing = 0;
        long sourced = 0;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            final LocalDate day = date;
            List<DailyReport> onDay = reports.stream()
                    .filter(r -> day.equals(r.getReportDate()))
                    .toList();

            Set<Long> distinct = new HashSet<>();
            onDay.forEach(r -> {
                if (r.getEmployee() != null) distinct.add(r.getEmployee().getEmployeeId());
            });

            long dayMissing = 0;
            if (isWorkingDay(day)) {
                for (Long employeeId : expectedIds) {
                    if (!distinct.contains(employeeId)
                            && !onLeave(employeeId, day, leaves)) {
                        dayMissing++;
                    }
                }
            }
            missing += dayMissing;

            long daySourced = onDay.stream()
                    .mapToLong(r -> platformFilter == null
                            ? nz(r.getCandidatesSourced())
                            : reportSourcedFor(r, platformFilter))
                    .sum();
            sourced += daySourced;

            days.add(new DailyReportDay(day, onDay.size(), distinct.size(), daySourced, dayMissing));
        }

        return new DailyReportsSection(
                eligible, reports.size(), sourced, missing, days,
                "Expected reports are working days (Monday to Saturday), excluding days covered by approved leave. "
                        + "Missing reports are shown as missing, not as zero productivity; employees are counted only once they have at least one report in the range.");
    }

    /**
     * Saturday is a working day in this company: only Sunday is a weekly holiday.
     * Expected-report counts therefore include Saturday, and a missing Saturday
     * report is shown as missing like any other working day.
     */
    private boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private boolean onLeave(Long employeeId, LocalDate date, List<LeaveManagement> leaves) {
        for (LeaveManagement leave : leaves) {
            if (leave.getEmployee() == null
                    || !employeeId.equals(leave.getEmployee().getEmployeeId())) {
                continue;
            }
            if (leave.getStartDate() == null || leave.getEndDate() == null) continue;
            if (!date.isBefore(leave.getStartDate()) && !date.isAfter(leave.getEndDate())) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- sourcing

    private SourcedProfilesSection buildSourcedProfiles(
            List<Employee> employees,
            List<DailyReport> reports,
            List<Feedback> candidates,
            List<Feedback> platformCandidates,
            Set<Long> candidateEmployeesAny,
            int candidatesWithoutDate,
            LocalDate from, LocalDate to,
            String platformFilter) {

        Map<Long, Long> reportCounts = new HashMap<>();
        Map<Long, Map<String, Long>> reportPlatforms = new HashMap<>();
        Map<Long, Map<LocalDate, Long>> reportTrend = new HashMap<>();

        for (DailyReport report : reports) {
            Long empId = report.getEmployee().getEmployeeId();
            long count = nz(report.getCandidatesSourced());
            Map<String, Long> platforms = effectiveReportPlatforms(report);
            if (platformFilter != null) {
                count = platforms.getOrDefault(platformFilter, 0L);
                platforms = count == 0 ? Map.of()
                        : Map.of(platformFilter, count);
            }
            if (count == 0) continue;
            reportCounts.merge(empId, count, Long::sum);
            reportPlatforms.computeIfAbsent(empId, k -> new LinkedHashMap<>())
                    .merge(platformFilter == null ? platformLabel(report.getSourcingPlatform()) : platformFilter,
                            count, Long::sum);
            if (report.getReportDate() != null) {
                reportTrend.computeIfAbsent(empId, k -> new LinkedHashMap<>())
                        .merge(report.getReportDate(), count, Long::sum);
            }
        }

        Map<Long, Long> candidateCounts = new HashMap<>();
        Map<Long, Map<String, Long>> candidatePlatforms = new HashMap<>();
        Map<Long, Map<LocalDate, Long>> candidateTrend = new HashMap<>();
        Map<Long, String> names = new HashMap<>();

        for (Feedback candidate : platformCandidates) {
            if (candidate.getEmployee() == null || candidate.getSourcedDate() == null) continue;
            Long empId = candidate.getEmployee().getEmployeeId();
            names.put(empId, candidate.getEmployee().getEmployeeName());
            candidateCounts.merge(empId, 1L, Long::sum);
            candidatePlatforms.computeIfAbsent(empId, k -> new LinkedHashMap<>())
                    .merge(platformLabel(candidate.getSourcingPlatform()), 1L, Long::sum);
            candidateTrend.computeIfAbsent(empId, k -> new LinkedHashMap<>())
                    .merge(candidate.getSourcedDate(), 1L, Long::sum);
        }

        Map<Long, Map<LocalDate, Long>> trend = new LinkedHashMap<>();
        Map<String, Long> byPlatform = new LinkedHashMap<>();
        List<EmployeeSourceRow> rows = new ArrayList<>();

        for (Employee employee : employees) {
            Long empId = employee.getEmployeeId();
            boolean useCandidates = candidateEmployeesAny.contains(empId);
            long count = useCandidates
                    ? candidateCounts.getOrDefault(empId, 0L)
                    : reportCounts.getOrDefault(empId, 0L);
            if (count == 0) continue;

            names.put(empId, employee.getEmployeeName());
            rows.add(new EmployeeSourceRow(
                    empId, employee.getEmployeeName(), count,
                    useCandidates ? "CANDIDATES" : "REPORTS"));

            Map<String, Long> platformMap = useCandidates
                    ? candidatePlatforms.getOrDefault(empId, Map.of())
                    : reportPlatforms.getOrDefault(empId, Map.of());
            platformMap.forEach((name, value) -> byPlatform.merge(name, value, Long::sum));

            Map<LocalDate, Long> sourceTrend = useCandidates
                    ? candidateTrend.getOrDefault(empId, Map.of())
                    : reportTrend.getOrDefault(empId, Map.of());
            sourceTrend.forEach((date, value) ->
                    trend.computeIfAbsent(empId, k -> new LinkedHashMap<>()).merge(date, value, Long::sum));
        }

        rows.sort(Comparator.comparingLong(EmployeeSourceRow::sourcedProfiles).reversed()
                .thenComparing(EmployeeSourceRow::employeeName,
                        Comparator.nullsLast(String::compareToIgnoreCase)));

        List<SourcedPoint> dailyTrend = trend.entrySet().stream()
                .flatMap(entry -> entry.getValue().entrySet().stream()
                        .map(e -> new SourcedPoint(e.getKey(), e.getValue())))
                .collect(java.util.stream.Collectors.groupingBy(
                        SourcedPoint::date,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.summingLong(SourcedPoint::sourcedProfiles)))
                .entrySet().stream()
                .map(e -> new SourcedPoint(e.getKey(), e.getValue()))
                .toList();

        long total = rows.stream().mapToLong(EmployeeSourceRow::sourcedProfiles).sum();
        long unknown = byPlatform.getOrDefault(UNKNOWN, 0L);

        return new SourcedProfilesSection(
                total, rows, dailyTrend, toPlatformCounts(byPlatform),
                unknown, candidatesWithoutDate,
                "Candidate records with a sourced date are authoritative. Daily report counts are used only for employees with no candidate records in the range, so one profile is never counted twice. Blank platforms and historical dates are shown as Unknown.");
    }

    private List<PlatformCount> toPlatformCounts(Map<String, Long> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(String::compareToIgnoreCase)))
                .map(e -> new PlatformCount(e.getKey(), e.getValue()))
                .toList();
    }

    // ---------------------------------------------------------------- targets

    private RevenueTargetSection buildTargets(
            List<Employee> employees,
            List<WeeklyReport> weeks,
            Map<Long, List<EmployeeSalaryHistory>> history,
            LocalDate from, LocalDate to) {

        Map<Long, BigDecimal> revenueByEmployee = new HashMap<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (WeeklyReport week : weeks) {
            if (week.getRevenueGenerated() == null) continue;
            BigDecimal value = week.getRevenueGenerated();
            totalRevenue = totalRevenue.add(value);
            if (week.getEmployee() != null) {
                revenueByEmployee.merge(
                        week.getEmployee().getEmployeeId(), value, BigDecimal::add);
            }
        }

        List<YearMonth> months = months(from, to);
        List<EmployeeTargetRow> rows = new ArrayList<>();
        BigDecimal totalTarget = BigDecimal.ZERO;
        long missing = 0;

        for (Employee employee : employees) {
            List<EmployeeSalaryHistory> rowsHistory = history
                    .getOrDefault(employee.getEmployeeId(), List.of());

            BigDecimal target = BigDecimal.ZERO;
            boolean configured = true;
            int monthsCounted = 0;
            BigDecimal latestSalary = null;

            for (YearMonth month : months) {
                BigDecimal salary = salaryFor(rowsHistory, month);
                if (salary == null) {
                    configured = false;
                } else {
                    target = target.add(salary.multiply(THREE));
                    latestSalary = salary;
                    monthsCounted++;
                }
            }

            BigDecimal revenue = revenueByEmployee.getOrDefault(
                    employee.getEmployeeId(), BigDecimal.ZERO);

            BigDecimal rawPercent = null;
            BigDecimal cappedPercent = null;
            BigDecimal remaining = null;
            BigDecimal excess = null;

            if (configured) {
                totalTarget = totalTarget.add(target);
                if (target.signum() > 0) {
                    rawPercent = revenue.multiply(HUNDRED)
                            .divide(target, 2, RoundingMode.HALF_UP);
                    cappedPercent = rawPercent.min(HUNDRED);
                }
                remaining = target.subtract(revenue).max(BigDecimal.ZERO);
                excess = revenue.subtract(target).max(BigDecimal.ZERO);
            } else {
                missing++;
            }

            rows.add(new EmployeeTargetRow(
                    employee.getEmployeeId(),
                    employee.getEmployeeName(),
                    employee.getRole(),
                    latestSalary,
                    configured ? money(target) : null,
                    money(revenue),
                    remaining == null ? null : money(remaining),
                    excess == null ? null : money(excess),
                    cappedPercent,
                    rawPercent,
                    configured,
                    monthsCounted));
        }

        rows.sort(Comparator.comparing(
                EmployeeTargetRow::targetConfigured,
                Comparator.reverseOrder())
                .thenComparing(EmployeeTargetRow::employeeName,
                        Comparator.nullsLast(String::compareToIgnoreCase)));

        return new RevenueTargetSection(
                money(totalTarget), money(totalRevenue), missing, rows,
                months.stream().map(YearMonth::toString).toList(),
                partialMonths(from, to),
                "Target is salary effective in each month x 3. Historical months keep the salary that applied then; changing today's salary does not rewrite them. Partial months are labelled and are not annualised.");
    }

    private BigDecimal salaryFor(List<EmployeeSalaryHistory> rows, YearMonth month) {
        LocalDate first = month.atDay(1);
        BigDecimal salary = null;
        LocalDate found = null;
        for (EmployeeSalaryHistory row : rows) {
            if (row.getEffectiveMonth() != null
                    && !row.getEffectiveMonth().isAfter(first)
                    && (found == null || row.getEffectiveMonth().isAfter(found))) {
                found = row.getEffectiveMonth();
                salary = row.getSalary();
            }
        }
        return salary;
    }

    // ---------------------------------------------------------------- budgets

    private ClientBudgetSection buildBudgets(
            Long adminId, Long scopeEmployeeId, List<Client> clients,
            List<WeeklyReport> weeks, List<DailyReport> reports,
            List<Feedback> candidates,
            LocalDate from, LocalDate to) {

        Map<Long, BigDecimal> revenueByClient = new LinkedHashMap<>();
        Map<Long, Map<Long, BigDecimal>> employeesByClient = new LinkedHashMap<>();
        BigDecimal unattributed = BigDecimal.ZERO;

        for (WeeklyReport week : weeks) {
            BigDecimal revenue = week.getRevenueGenerated() == null
                    ? BigDecimal.ZERO : week.getRevenueGenerated();
            if (week.getClient() == null) {
                unattributed = unattributed.add(revenue);
                continue;
            }
            Long clientId = week.getClient().getClientId();
            revenueByClient.merge(clientId, revenue, BigDecimal::add);
            if (week.getEmployee() != null) {
                employeesByClient.computeIfAbsent(clientId, k -> new LinkedHashMap<>())
                        .merge(week.getEmployee().getEmployeeId(), revenue, BigDecimal::add);
            }
        }

        Set<Long> activityClientIds = new HashSet<>();
        weeks.forEach(w -> {
            if (w.getClient() != null) activityClientIds.add(w.getClient().getClientId());
        });
        reports.forEach(r -> {
            if (r.getClient() != null) activityClientIds.add(r.getClient().getClientId());
        });
        candidates.forEach(c -> clients.stream()
                .filter(cl -> candidateMatchesClient(c, cl))
                .forEach(cl -> activityClientIds.add(cl.getClientId())));

        List<ClientBudgetRow> rows = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalRemaining = BigDecimal.ZERO;

        for (Client client : clients) {
            Long id = client.getClientId();
            if (scopeEmployeeId != null && !activityClientIds.contains(id)) continue;

            BigDecimal revenue = money(revenueByClient.getOrDefault(id, BigDecimal.ZERO));
            totalRevenue = totalRevenue.add(revenue);

            Integer openings = client.getNumberOfPositions();
            BigDecimal perCandidate = client.getBudgetPerCandidate();
            boolean configured = openings != null && openings > 0
                    && perCandidate != null && perCandidate.signum() > 0;

            BigDecimal budget = configured
                    ? perCandidate.multiply(BigDecimal.valueOf(openings))
                    : null;
            BigDecimal remaining = null;
            BigDecimal coverage = null;

            if (budget != null) {
                totalBudget = totalBudget.add(budget);
                remaining = budget.subtract(revenue).max(BigDecimal.ZERO);
                totalRemaining = totalRemaining.add(remaining);
                if (budget.signum() > 0) {
                    coverage = revenue.multiply(HUNDRED)
                            .divide(budget, 2, RoundingMode.HALF_UP);
                }
            }

            List<ContributionRow> contributions = new ArrayList<>();
            Map<Long, BigDecimal> byEmployee = employeesByClient.getOrDefault(id, Map.of());
            for (Map.Entry<Long, BigDecimal> entry : byEmployee.entrySet()) {
                String name = weeks.stream()
                        .filter(w -> w.getEmployee() != null
                                && entry.getKey().equals(w.getEmployee().getEmployeeId()))
                        .map(w -> w.getEmployee().getEmployeeName())
                        .findFirst().orElse("Employee " + entry.getKey());
                contributions.add(new ContributionRow(entry.getKey(), name, money(entry.getValue())));
            }
            contributions.sort(Comparator.comparing(ContributionRow::revenue).reversed());

            rows.add(new ClientBudgetRow(
                    id,
                    client.getClientCode(),
                    client.getClientName(),
                    client.getPosition(),
                    openings,
                    perCandidate,
                    budget == null ? null : money(budget),
                    revenue,
                    remaining == null ? null : money(remaining),
                    coverage,
                    configured,
                    contributions));
        }

        rows.sort(Comparator.comparing(ClientBudgetRow::clientName,
                Comparator.nullsLast(String::compareToIgnoreCase)));

        return new ClientBudgetSection(
                money(totalBudget), money(totalRevenue), money(totalRemaining),
                money(unattributed), rows,
                "Position budget = openings x budget per candidate. Client budget sums its positions. Actual revenue is the recognised weekly revenue already attributed to that client; budget, salary, fees and revenue are not mixed. Revenue on weeks without a client is reported separately.");
    }

    private boolean candidateMatchesClient(Feedback candidate, Client client) {
        String candidateClient = candidateClient(candidate);
        if (candidateClient == null || client.getClientName() == null) return false;
        return candidateClient.equalsIgnoreCase(client.getClientName().trim());
    }

    // -------------------------------------------------------------- platforms

    private List<PlatformEffectivenessRow> buildPlatformRows(
            List<DailyReport> reports,
            List<Feedback> platformCandidates,
            List<Feedback> allCandidates,
            Set<Long> candidateEmployeesAny,
            String platformFilter) {

        Map<String, Long> sourced = new LinkedHashMap<>();
        Map<String, Long> tracked = new LinkedHashMap<>();
        Map<String, long[]> outcomes = new LinkedHashMap<>();
        Map<String, Boolean> fromReports = new HashMap<>();
        Map<String, Boolean> fromCandidates = new HashMap<>();

        for (Feedback candidate : platformCandidates) {
            String platform = platformLabel(candidate.getSourcingPlatform());
            sourced.merge(platform, 1L, Long::sum);
            tracked.merge(platform, 1L, Long::sum);
            fromCandidates.put(platform, true);
            long[] counts = outcomes.computeIfAbsent(platform, k -> new long[4]);
            if (isShortlisted(candidate.getStatus())) counts[0]++;
            if (isInterview(candidate.getStatus())) counts[1]++;
            if (isOffer(candidate.getStatus())) counts[2]++;
            if (isJoined(candidate.getStatus())) counts[3]++;
        }

        for (DailyReport report : reports) {
            if (report.getEmployee() == null
                    || candidateEmployeesAny.contains(report.getEmployee().getEmployeeId())) {
                continue;
            }
            Map<String, Long> counts = effectiveReportPlatforms(report);
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                if (platformFilter != null
                        && !entry.getKey().equalsIgnoreCase(platformFilter)) {
                    continue;
                }
                sourced.merge(entry.getKey(), entry.getValue(), Long::sum);
                fromReports.put(entry.getKey(), true);
            }
        }

        List<PlatformEffectivenessRow> rows = new ArrayList<>();
        for (Map.Entry<String, Long> entry : sourced.entrySet()) {
            String platform = entry.getKey();
            long total = entry.getValue();
            long trackedCount = tracked.getOrDefault(platform, 0L);
            long[] counts = outcomes.getOrDefault(platform, new long[4]);

            BigDecimal rate = trackedCount == 0 ? null
                    : BigDecimal.valueOf(counts[3]).multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(trackedCount), 2, RoundingMode.HALF_UP);

            boolean report = Boolean.TRUE.equals(fromReports.get(platform));
            boolean candidate = Boolean.TRUE.equals(fromCandidates.get(platform));
            String source = report && candidate ? "MIXED"
                    : candidate ? "CANDIDATES" : "REPORTS";

            String note = report
                    ? "Some or all counts come from daily reports, which do not record individual outcomes; their conversion is not tracked."
                    : "Counts and outcomes come from candidate records.";

            rows.add(new PlatformEffectivenessRow(
                    platform, total, trackedCount,
                    counts[0], counts[1], counts[2], counts[3],
                    rate, trackedCount,
                    null, source, note));
        }

        rows.sort(Comparator.comparingLong(PlatformEffectivenessRow::sourcedProfiles).reversed()
                .thenComparing(PlatformEffectivenessRow::platform, String::compareToIgnoreCase));
        return rows;
    }

    private List<PipelineStage> buildPipeline(List<Feedback> candidates) {
        Map<String, Long> counts = new HashMap<>();
        for (Feedback candidate : candidates) {
            String stage = pipelineStage(candidate.getStatus());
            counts.merge(stage, 1L, Long::sum);
        }
        List<String> order = List.of(
                "Sourced", "Screened", "Submitted", "Interview",
                "Offered", "Joined", "Rejected", "Withdrawn", "Unknown");
        return order.stream()
                .map(stage -> new PipelineStage(stage, counts.getOrDefault(stage, 0L)))
                .toList();
    }

    private String pipelineStage(String status) {
        if (status == null) return "Unknown";
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "SOURCED" -> "Sourced";
            case "SCREENED" -> "Screened";
            case "SUBMITTED" -> "Submitted";
            case "INTERVIEW_SCHEDULED", "INTERVIEW_ATTENDED", "INTERVIEW" -> "Interview";
            case "SELECTED", "OFFERED" -> "Offered";
            case "JOINED" -> "Joined";
            case "REJECTED" -> "Rejected";
            case "WITHDRAWN", "DROPPED" -> "Withdrawn";
            default -> "Unknown";
        };
    }

    private CandidateRow toCandidateRow(Feedback candidate) {
        return new CandidateRow(
                candidate.getFeedbackId(),
                candidate.getEmployee() == null ? null : candidate.getEmployee().getEmployeeId(),
                candidate.getEmployee() == null ? null : candidate.getEmployee().getEmployeeName(),
                candidate.getCandidateName(),
                candidate.getStatus(),
                candidate.getSourcedDate(),
                candidate.getSourcingPlatform(),
                candidateClient(candidate));
    }

    // ------------------------------------------------------------------ utils

    private Map<String, Long> effectiveReportPlatforms(DailyReport report) {
        Map<String, Long> result = new LinkedHashMap<>();
        int sourced = nz(report.getCandidatesSourced());
        if (sourced <= 0) return result;

        if (report.getPlatformCounts() != null && !report.getPlatformCounts().isEmpty()) {
            for (var row : report.getPlatformCounts()) {
                long count = row.getProfileCount() == null ? 0 : row.getProfileCount();
                if (count > 0) {
                    result.merge(platformLabel(row.getPlatform()), count, Long::sum);
                }
            }
            if (!result.isEmpty()) return result;
        }
        result.put(platformLabel(report.getSourcingPlatform()), (long) sourced);
        return result;
    }

    private long reportSourcedFor(DailyReport report, String platform) {
        Map<String, Long> counts = effectiveReportPlatforms(report);
        return counts.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(platform))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private boolean sourcedInRange(Feedback candidate, LocalDate from, LocalDate to) {
        return candidate.getSourcedDate() != null
                && !candidate.getSourcedDate().isBefore(from)
                && !candidate.getSourcedDate().isAfter(to);
    }

    private boolean matchesCandidateClient(
            Feedback candidate, Long clientId, Set<String> clientNames, String position) {
        if (clientId == null && position == null) return true;
        String name = candidateClient(candidate);
        if (name == null) return false;
        return clientNames.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private String candidateClient(Feedback candidate) {
        String value = candidate.getClientName();
        if (value == null || value.isBlank()) value = candidate.getClient();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String platformLabel(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value.trim();
    }

    private boolean isShortlisted(String status) {
        if (status == null) return false;
        return Set.of("SCREENED", "SUBMITTED", "INTERVIEW_SCHEDULED",
                "INTERVIEW_ATTENDED", "SELECTED", "OFFERED", "JOINED")
                .contains(status.toUpperCase(Locale.ROOT));
    }

    private boolean isInterview(String status) {
        if (status == null) return false;
        return status.equalsIgnoreCase("INTERVIEW_SCHEDULED")
                || status.equalsIgnoreCase("INTERVIEW_ATTENDED");
    }

    private boolean isOffer(String status) {
        if (status == null) return false;
        return status.equalsIgnoreCase("SELECTED")
                || status.equalsIgnoreCase("OFFERED")
                || status.equalsIgnoreCase("JOINED");
    }

    private boolean isJoined(String status) {
        return status != null && status.equalsIgnoreCase("JOINED");
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Dates must use YYYY-MM-DD");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private List<YearMonth> months(LocalDate from, LocalDate to) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.from(from);
        YearMonth last = YearMonth.from(to);
        while (!current.isAfter(last)) {
            months.add(current);
            current = current.plusMonths(1);
        }
        return months;
    }

    private List<String> monthsBetween(LocalDate from, LocalDate to) {
        return months(from, to).stream().map(YearMonth::toString).toList();
    }

    private boolean partialMonths(LocalDate from, LocalDate to) {
        return from.getDayOfMonth() != 1
                || to.getDayOfMonth() != to.lengthOfMonth();
    }
}
