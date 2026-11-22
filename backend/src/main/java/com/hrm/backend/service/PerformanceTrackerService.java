package com.hrm.backend.service;

import com.hrm.backend.dto.EmployeeTrackerDTO;
import com.hrm.backend.dto.PerformanceTrackerDTO;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.Note;
import com.hrm.backend.entity.WeeklyReport;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.NoteRepository;
import com.hrm.backend.repository.WeeklyReportRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performance numbers for the admin tracker and the employee tracker.
 *
 * Rules, identical in both places so the admin and the employee see the same maths:
 *
 *  - Daily resumes  = SUM(daily_reports.profiles_submitted) for the day/month.
 *    Reports are unique per employee and date (enforced when they are filed), and
 *    each stored row is counted exactly once, so nothing is double counted.
 *  - Monthly revenue = SUM(weekly_reports.revenue_generated) for the weeks that
 *    belong to the month. A week belongs to the month containing its
 *    weekStartDate: every report lands in exactly one month, months with four or
 *    five weeks both work, and a week that spans a month boundary is never
 *    counted twice.
 *  - Employees with no reports are still returned, with zeros.
 *  - Daily target is 5 resumes; monthly target is the employee's salary x 3.
 */
@Service
public class PerformanceTrackerService {

    /** Resumes expected per working day. */
    public static final int DAILY_RESUME_TARGET = 5;

    /** Monthly revenue target = salary x 3. */
    public static final int SALARY_MULTIPLIER = 3;

    private final EmployeeRepository employeeRepository;
    private final DailyReportRepository dailyReportRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final NoteRepository noteRepository;
    private final CompanyTimeService companyTime;

    public PerformanceTrackerService(
            EmployeeRepository employeeRepository,
            DailyReportRepository dailyReportRepository,
            WeeklyReportRepository weeklyReportRepository,
            NoteRepository noteRepository,
            CompanyTimeService companyTime) {

        this.employeeRepository = employeeRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.weeklyReportRepository = weeklyReportRepository;
        this.noteRepository = noteRepository;
        this.companyTime = companyTime;
    }

    /* --------------------------- ADMIN: one month --------------------------- */

    @Transactional(readOnly = true)
    public PerformanceTrackerDTO getMonthlyTracker(Long adminId, String monthParam) {

        YearMonth month = parseMonth(monthParam);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<Employee> employees = employeeRepository.findByCompanyOf(adminId);

        List<DailyReport> dailyReports =
                dailyReportRepository.findByEmployeeInCompanyOfAndReportDateBetween(adminId, start, end);

        List<WeeklyReport> weeklyReports =
                weeklyReportRepository.findByEmployeeInCompanyOfAndWeekStartDateBetween(adminId, start, end);

        List<Note> notes = noteRepository.findByEmployeeInCompanyOfAndCreatedAtBetween(
                adminId, start.atStartOfDay(), end.atTime(23, 59, 59));

        // employeeId -> {resumes, reports, distinct days}
        Map<Long, long[]> dailyAggregate = new LinkedHashMap<>();
        Map<Long, Set<LocalDate>> days = new LinkedHashMap<>();

        for (DailyReport report : dailyReports) {
            Long employeeId = report.getEmployee().getEmployeeId();
            long[] bucket = dailyAggregate.computeIfAbsent(employeeId, key -> new long[3]);
            bucket[0] += nz(report.getProfilesSubmitted());
            bucket[1] += 1;
            if (report.getReportDate() != null) {
                days.computeIfAbsent(employeeId, key -> new HashSet<>()).add(report.getReportDate());
            }
        }

        // employeeId -> weekly rows
        Map<Long, List<WeeklyReport>> weeklyByEmployee = new LinkedHashMap<>();
        for (WeeklyReport report : weeklyReports) {
            weeklyByEmployee
                    .computeIfAbsent(report.getEmployee().getEmployeeId(), key -> new ArrayList<>())
                    .add(report);
        }

        Map<Long, List<Note>> notesByEmployee = new LinkedHashMap<>();
        for (Note note : notes) {
            if (note.getEmployee() == null) continue;
            notesByEmployee
                    .computeIfAbsent(note.getEmployee().getEmployeeId(), key -> new ArrayList<>())
                    .add(note);
        }

        // ---- Part 1: leaderboard ----
        List<PerformanceTrackerDTO.LeaderboardRow> rows = new ArrayList<>();
        for (Employee employee : employees) {
            Long employeeId = employee.getEmployeeId();
            long[] bucket = dailyAggregate.getOrDefault(employeeId, new long[3]);
            long distinctDays = days.getOrDefault(employeeId, Set.of()).size();
            double average = distinctDays == 0
                    ? 0d
                    : round((double) bucket[0] / distinctDays);

            rows.add(new PerformanceTrackerDTO.LeaderboardRow(
                    0,
                    employeeId,
                    employee.getEmployeeName(),
                    employee.getRole(),
                    bucket[0],
                    bucket[1],
                    distinctDays,
                    average));
        }

        rows.sort(Comparator
                .comparingLong(PerformanceTrackerDTO.LeaderboardRow::resumesSubmitted).reversed()
                .thenComparing(Comparator.comparingLong(PerformanceTrackerDTO.LeaderboardRow::reportsFiled).reversed())
                .thenComparing(PerformanceTrackerDTO.LeaderboardRow::employeeName,
                        String.CASE_INSENSITIVE_ORDER));

        List<PerformanceTrackerDTO.LeaderboardRow> ranked = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            PerformanceTrackerDTO.LeaderboardRow row = rows.get(i);
            ranked.add(new PerformanceTrackerDTO.LeaderboardRow(
                    i + 1,
                    row.employeeId(),
                    row.employeeName(),
                    row.role(),
                    row.resumesSubmitted(),
                    row.reportsFiled(),
                    row.daysReported(),
                    row.averageResumesPerDay()));
        }

        long totalResumes = ranked.stream()
                .mapToLong(PerformanceTrackerDTO.LeaderboardRow::resumesSubmitted).sum();
        long totalReports = ranked.stream()
                .mapToLong(PerformanceTrackerDTO.LeaderboardRow::reportsFiled).sum();
        long employeesWithReports = ranked.stream()
                .filter(row -> row.reportsFiled() > 0)
                .count();

        // ---- Part 2: revenue report ----
        List<PerformanceTrackerDTO.EmployeeRevenue> revenueRows = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        int employeesWithRevenue = 0;
        int weeksCounted = 0;

        for (Employee employee : employees) {
            Long employeeId = employee.getEmployeeId();
            List<WeeklyReport> employeeWeeks = weeklyByEmployee.getOrDefault(employeeId, List.of());

            List<PerformanceTrackerDTO.WeekRow> weekRows = employeeWeeks.stream()
                    .sorted(Comparator.comparing(WeeklyReport::getWeekStartDate))
                    .map(report -> new PerformanceTrackerDTO.WeekRow(
                            report.getWeeklyReportId(),
                            report.getWeekNumber(),
                            report.getWeekStartDate(),
                            report.getWeekEndDate(),
                            nullToZero(report.getRevenueGenerated()),
                            report.getProfilesSubmitted(),
                            report.getInterviewsScheduled(),
                            report.getFinalSelections(),
                            report.getJoined(),
                            report.getClient() == null ? null : report.getClient().getClientName()))
                    .toList();

            BigDecimal employeeRevenue = weekRows.stream()
                    .map(PerformanceTrackerDTO.WeekRow::revenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalRevenue = totalRevenue.add(employeeRevenue);
            weeksCounted += weekRows.size();
            if (employeeRevenue.signum() > 0) {
                employeesWithRevenue++;
            }

            List<PerformanceTrackerDTO.NoteRow> noteRows = notesByEmployee
                    .getOrDefault(employeeId, List.of())
                    .stream()
                    .sorted(Comparator.comparing(Note::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(note -> new PerformanceTrackerDTO.NoteRow(
                            note.getNoteId(),
                            note.getTitle(),
                            note.getContent(),
                            note.getCreatedAt(),
                            note.getUpdatedAt()))
                    .toList();

            revenueRows.add(new PerformanceTrackerDTO.EmployeeRevenue(
                    employeeId,
                    employee.getEmployeeName(),
                    employee.getRole(),
                    employee.getSalary(),
                    employeeRevenue,
                    weekRows.size(),
                    weekRows,
                    noteRows));
        }

        revenueRows.sort(Comparator
                .comparing(PerformanceTrackerDTO.EmployeeRevenue::monthlyRevenue).reversed()
                .thenComparing(PerformanceTrackerDTO.EmployeeRevenue::employeeName,
                        String.CASE_INSENSITIVE_ORDER));

        return new PerformanceTrackerDTO(
                month.toString(),
                start,
                end,
                DAILY_RESUME_TARGET,
                new PerformanceTrackerDTO.Leaderboard(
                        ranked, totalResumes, totalReports, employeesWithReports),
                new PerformanceTrackerDTO.RevenueReport(
                        revenueRows, totalRevenue.setScale(2, RoundingMode.HALF_UP),
                        employeesWithRevenue, weeksCounted));
    }

    /* ------------------------- EMPLOYEE: own tracker ------------------------- */

    @Transactional(readOnly = true)
    public EmployeeTrackerDTO getEmployeeTracker(String authenticatedEmail, LocalDate date) {

        Employee employee = employeeRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee account not found"));

        // "Today" is the office day, not the UTC container day.
        LocalDate today = companyTime.todayForEmployee(employee);

        LocalDate day = date == null ? today : date;

        if (day.isAfter(today)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A future date has no performance yet");
        }

        Long employeeId = employee.getEmployeeId();
        Long adminId = employee.getAdmin().getAdminId();

        // ---- Part 1: daily resume target ----
        long resumesToday = dailyReportRepository
                .findByEmployeeEmployeeIdAndReportDateBetween(employeeId, day, day)
                .stream()
                .mapToLong(report -> nz(report.getProfilesSubmitted()))
                .sum();

        long remaining = Math.max(DAILY_RESUME_TARGET - resumesToday, 0);
        int dailyPercent = DAILY_RESUME_TARGET == 0
                ? 0
                : (int) Math.min(100, Math.round(resumesToday * 100d / DAILY_RESUME_TARGET));

        EmployeeTrackerDTO.DailyResumeTarget daily = new EmployeeTrackerDTO.DailyResumeTarget(
                DAILY_RESUME_TARGET,
                resumesToday,
                remaining,
                dailyPercent,
                resumesToday >= DAILY_RESUME_TARGET);

        // ---- Part 2: monthly revenue target ----
        YearMonth month = YearMonth.from(day);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        List<WeeklyReport> ownWeeks = weeklyReportRepository
                .findByEmployeeEmployeeIdAndWeekStartDateBetween(employeeId, monthStart, monthEnd);

        BigDecimal achieved = ownWeeks.stream()
                .map(report -> nullToZero(report.getRevenueGenerated()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal salary = employee.getSalary();
        BigDecimal target = salary == null
                ? null
                : salary.multiply(BigDecimal.valueOf(SALARY_MULTIPLIER)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal remainingRevenue = target == null
                ? null
                : target.subtract(achieved).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        int monthlyPercent = target == null || target.signum() == 0
                ? 0
                : (int) Math.min(100, Math.round(
                        achieved.multiply(BigDecimal.valueOf(100))
                                .divide(target, 6, RoundingMode.HALF_UP)
                                .doubleValue()));

        EmployeeTrackerDTO.MonthlyRevenueTarget revenue = new EmployeeTrackerDTO.MonthlyRevenueTarget(
                salary,
                target,
                achieved,
                remainingRevenue,
                monthlyPercent,
                target != null && achieved.compareTo(target) >= 0,
                ownWeeks.size());

        // ---- Part 3: same-day comparison, scoped to the employee's organisation ----
        Map<Long, Long> resumesByEmployee = new LinkedHashMap<>();
        for (DailyReport report :
                dailyReportRepository.findByEmployeeInCompanyOfAndReportDateBetween(adminId, day, day)) {
            resumesByEmployee.merge(
                    report.getEmployee().getEmployeeId(),
                    (long) nz(report.getProfilesSubmitted()),
                    Long::sum);
        }

        List<EmployeeTrackerDTO.ComparisonRow> comparison = employeeRepository
                .findByCompanyOf(adminId)
                .stream()
                .map(colleague -> new EmployeeTrackerDTO.ComparisonRow(
                        colleague.getEmployeeId(),
                        colleague.getEmployeeName(),
                        resumesByEmployee.getOrDefault(colleague.getEmployeeId(), 0L),
                        colleague.getEmployeeId().equals(employeeId)))
                .sorted(Comparator
                        .comparingLong(EmployeeTrackerDTO.ComparisonRow::resumes).reversed()
                        .thenComparing(EmployeeTrackerDTO.ComparisonRow::employeeName,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new EmployeeTrackerDTO(
                day,
                month.toString(),
                monthStart,
                monthEnd,
                daily,
                revenue,
                comparison);
    }

    /* -------------------------------- helpers -------------------------------- */

    private YearMonth parseMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(monthParam.trim());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Month must be YYYY-MM, for example 2026-11");
        }
    }

    private long nz(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /** Overload used by the dashboard, which only needs the month totals. */
    @Transactional(readOnly = true)
    public PerformanceTrackerDTO.RevenueReport getMonthlyRevenue(Long adminId, YearMonth month) {
        return getMonthlyTracker(adminId, month.toString()).revenue();
    }

    /** Exposed for the dashboard card's "as of" timestamp. */
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
