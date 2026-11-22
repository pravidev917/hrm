package com.hrm.backend.service;

import com.hrm.backend.dto.AdminAttendanceRowDTO;
import com.hrm.backend.dto.AdminAttendanceSummaryDTO;
import com.hrm.backend.dto.EmployeeAttendanceHistoryDTO;
import com.hrm.backend.entity.Attendance;
import com.hrm.backend.entity.AttendanceSession;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.Holiday;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.entity.WfhRequest;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.AttendanceSessionRepository;
import com.hrm.backend.repository.HolidayRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.WfhRequestRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Administrator views of the attendance monitor.
 *
 * Every number is derived from the session ledger (plus stored attendance rows and the
 * leave/WFH/holiday calendars), computed here for the whole company in a handful of
 * queries, so the same range shows the same figures to every administrator.
 */
@Service
public class AttendanceMonitorService {

    /** No comma in the pattern: these cells are written straight into CSV columns. */
    private static final DateTimeFormatter IST_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter CSV_DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final WfhRequestRepository wfhRepository;
    private final LeaveManagementRepository leaveRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeService employeeService;
    private final AdminService adminService;
    private final CompanyScopeService companyScope;
    private final CompanyTimeService companyTime;
    private final AttendanceSessionService sessionService;

    public AttendanceMonitorService(
            AttendanceSessionRepository sessionRepository,
            AttendanceRepository attendanceRepository,
            WfhRequestRepository wfhRepository,
            LeaveManagementRepository leaveRepository,
            HolidayRepository holidayRepository,
            EmployeeService employeeService,
            AdminService adminService,
            CompanyScopeService companyScope,
            CompanyTimeService companyTime,
            AttendanceSessionService sessionService) {

        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.wfhRepository = wfhRepository;
        this.leaveRepository = leaveRepository;
        this.holidayRepository = holidayRepository;
        this.employeeService = employeeService;
        this.adminService = adminService;
        this.companyScope = companyScope;
        this.companyTime = companyTime;
        this.sessionService = sessionService;
    }

    // ================= ADMIN: the monitor table =================

    /** One row per approved employee per day of the range, with the given filters. */
    @Transactional(readOnly = true)
    public List<AdminAttendanceRowDTO> rows(
            Long adminId,
            LocalDate from,
            LocalDate to,
            Long employeeId,
            String designation,
            String status,
            String query) {

        Long companyId = adminService.requireCompanyId(adminId);
        LocalDate today = companyTime.todayForCompany(companyId);

        // The monitor opens on today; a missing bound falls back to it.
        LocalDate start = from == null ? today : from;
        LocalDate end = to == null ? today : to;
        validateRange(start, end);

        CompanyData data = loadCompanyData(adminId, start, end, today, companyTime.now());
        AttendanceLedger ledger = data.ledger();

        boolean statusFilter = status != null && !status.isBlank();

        List<AdminAttendanceRowDTO> rows = new ArrayList<>();

        for (Employee employee : data.employees()) {

            if (employeeId != null && !employeeId.equals(employee.getEmployeeId())) {
                continue;
            }
            if (designation != null && !designation.isBlank()
                    && !designation.equalsIgnoreCase(employee.getRole())) {
                continue;
            }
            if (query != null && !query.isBlank()) {
                String needle = query.trim().toLowerCase();
                String name = employee.getEmployeeName() == null ? "" : employee.getEmployeeName().toLowerCase();
                String code = employee.getEmployeeCode() == null ? "" : employee.getEmployeeCode().toLowerCase();
                if (!name.contains(needle) && !code.contains(needle)) {
                    continue;
                }
            }

            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {

                if (employee.getJoiningDate() != null && cursor.isBefore(employee.getJoiningDate())) {
                    cursor = cursor.plusDays(1);
                    continue;
                }

                AttendanceLedger.DayFacts facts = ledger.day(employee.getEmployeeId(), cursor);

                if (!statusFilter || status.equalsIgnoreCase(facts.status())) {
                    rows.add(toRow(employee, facts));
                }

                cursor = cursor.plusDays(1);
            }
        }

        rows.sort(Comparator
                .comparing(AdminAttendanceRowDTO::attendanceDate).reversed()
                .thenComparing(AdminAttendanceRowDTO::employeeName, String.CASE_INSENSITIVE_ORDER));

        return rows;
    }

    /** Employees who are logged in right now, plus sessions left open on earlier days. */
    @Transactional(readOnly = true)
    public List<AdminAttendanceRowDTO> active(Long adminId) {

        Long companyId = adminService.requireCompanyId(adminId);
        LocalDate today = companyTime.todayForCompany(companyId);
        Instant now = companyTime.now();

        List<AttendanceSession> open = sessionRepository.findActiveInCompanyOf(adminId);
        if (open.isEmpty()) {
            return List.of();
        }

        LocalDate earliest = open.stream()
                .map(AttendanceSession::getAttendanceDate)
                .min(LocalDate::compareTo)
                .orElse(today);

        CompanyData data = loadCompanyData(adminId, earliest, today, today, now);
        AttendanceLedger ledger = data.ledger();

        List<AdminAttendanceRowDTO> rows = new ArrayList<>();

        for (AttendanceSession session : open) {

            Employee employee = session.getEmployee();
            if (employee == null) {
                continue;
            }

            AttendanceLedger.DayFacts facts = ledger.day(employee.getEmployeeId(), session.getAttendanceDate());

            boolean stale = session.getAttendanceDate().isBefore(today);

            rows.add(new AdminAttendanceRowDTO(
                    employee.getEmployeeId(),
                    employee.getEmployeeCode(),
                    employee.getEmployeeName(),
                    employee.getRole(),
                    facts.date(),
                    facts.firstLoginAt(),
                    facts.lastLogoutAt(),
                    facts.totalWorkSeconds(),
                    facts.sessionCount(),
                    stale ? AttendanceDayStatus.INCOMPLETE_SESSION : AttendanceDayStatus.LOGGED_IN,
                    facts.workingDay(),
                    facts.workMode(),
                    facts.wfhApproved(),
                    facts.onLeave(),
                    session.getSessionId(),
                    session.getLoginAt(),
                    stale ? 0L : Math.max(0L, java.time.Duration.between(session.getLoginAt(), now).getSeconds()),
                    facts.recordId()));
        }

        rows.sort(Comparator
                .comparing((AdminAttendanceRowDTO row) ->
                        !AttendanceDayStatus.INCOMPLETE_SESSION.equals(row.status()))
                .thenComparing(AdminAttendanceRowDTO::employeeName, String.CASE_INSENSITIVE_ORDER));

        return rows;
    }

    /** Full history of one employee with every login/logout pair. */
    @Transactional(readOnly = true)
    public EmployeeAttendanceHistoryDTO employeeHistory(
            Long adminId,
            Long employeeId,
            LocalDate from,
            LocalDate to) {

        adminService.requireCompanyId(adminId);

        Employee employee = employeeService.getEmployeeById(employeeId);
        companyScope.requireManages(adminId, employee);

        LocalDate today = companyTime.todayForEmployee(employee);
        LocalDate start = from == null ? today.withDayOfMonth(1) : from;
        LocalDate end = to == null ? today : to;

        return sessionService.buildHistory(employee, start, end, today, companyTime.now());
    }

    // ================= ADMIN: summary =================

    @Transactional(readOnly = true)
    public AdminAttendanceSummaryDTO summary(Long adminId, LocalDate from, LocalDate to) {

        Long companyId = adminService.requireCompanyId(adminId);
        LocalDate today = companyTime.todayForCompany(companyId);
        Instant now = companyTime.now();

        LocalDate start = from == null ? today.withDayOfMonth(1) : from;
        LocalDate end = to == null ? today : to;
        validateRange(start, end);

        CompanyData data = loadCompanyData(adminId, start, end, today, now);
        AttendanceLedger ledger = data.ledger();
        List<Employee> employees = data.employees();
        List<LocalDate> dates = data.days();

        // --- per date ---
        List<AdminAttendanceSummaryDTO.DailyTotal> daily = new ArrayList<>();
        for (LocalDate date : dates) {
            long sessionCount = 0;
            long workSeconds = 0;
            long loggedInEmployees = 0;
            for (Employee employee : employees) {
                AttendanceLedger.DayFacts facts = ledger.day(employee.getEmployeeId(), date);
                sessionCount += facts.sessionCount();
                workSeconds += facts.totalWorkSeconds();
                if (AttendanceDayStatus.LOGGED_IN.equals(facts.status())) {
                    loggedInEmployees++;
                }
            }
            daily.add(new AdminAttendanceSummaryDTO.DailyTotal(date, sessionCount, workSeconds, loggedInEmployees));
        }

        // --- per employee ---
        List<AdminAttendanceSummaryDTO.EmployeeTotal> employeeTotals = new ArrayList<>();
        for (Employee employee : employees) {
            long daysWorked = 0;
            long sessionCount = 0;
            long workSeconds = 0;
            long incomplete = 0;
            for (LocalDate date : dates) {
                AttendanceLedger.DayFacts facts = ledger.day(employee.getEmployeeId(), date);
                if (facts.sessionCount() > 0) {
                    sessionCount += facts.sessionCount();
                    workSeconds += facts.totalWorkSeconds();
                }
                if (isWorkedStatus(facts.status())) {
                    daysWorked++;
                }
                if (AttendanceDayStatus.INCOMPLETE_SESSION.equals(facts.status())) {
                    incomplete++;
                }
            }
            if (sessionCount == 0 && daysWorked == 0 && incomplete == 0) {
                continue;
            }
            employeeTotals.add(new AdminAttendanceSummaryDTO.EmployeeTotal(
                    employee.getEmployeeId(),
                    employee.getEmployeeCode(),
                    employee.getEmployeeName(),
                    employee.getRole(),
                    daysWorked,
                    sessionCount,
                    workSeconds,
                    incomplete));
        }

        // --- totals of the requested range ---
        AdminAttendanceSummaryDTO.RangeTotals rangeTotals = totals(ledger, employees, dates);

        // --- weekly (Monday of this week .. today) and monthly ---
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        List<LocalDate> weekDates = datesBetween(weekStart, today);
        AdminAttendanceSummaryDTO.RangeTotals weekly = totals(ledger, employees, weekDates);

        LocalDate monthStart = today.withDayOfMonth(1);
        List<LocalDate> monthDates = datesBetween(monthStart, today);
        AdminAttendanceSummaryDTO.RangeTotals monthly = totals(ledger, employees, monthDates);

        // --- today's snapshot ---
        AdminAttendanceSummaryDTO.Today todayCounts = countToday(ledger, employees, today);

        return new AdminAttendanceSummaryDTO(start, end, today, todayCounts, weekly, monthly,
                rangeTotals, daily, employeeTotals);
    }

    // ================= ADMIN: export =================

    /** Plain CSV of the exact rows the monitor table shows, for the same filters. */
    @Transactional(readOnly = true)
    public String csv(
            Long adminId,
            LocalDate from,
            LocalDate to,
            Long employeeId,
            String designation,
            String status,
            String query) {

        Long companyId = adminService.requireCompanyId(adminId);
        ZoneId zone = companyTime.zoneForCompany(companyId);

        List<AdminAttendanceRowDTO> rows = rows(adminId, from, to, employeeId, designation, status, query);

        StringBuilder out = new StringBuilder("\uFEFF"); // BOM so Excel reads UTF-8
        out.append("Employee ID,Employee Name,Designation,Attendance Date,Status,")
           .append("First Login (IST),Last Logout (IST),Total Working Hours,Login Sessions\n");

        for (AdminAttendanceRowDTO row : rows) {
            out.append(escape(row.employeeCode())).append(',')
               .append(escape(row.employeeName())).append(',')
               .append(escape(row.designation())).append(',')
               .append(CSV_DAY.format(row.attendanceDate())).append(',')
               .append(escape(row.status())).append(',')
               .append(ist(zone, row.firstLoginAt())).append(',')
               .append(ist(zone, row.lastLogoutAt())).append(',')
               .append(row.totalWorkSeconds() > 0
                       ? String.format(Locale.ROOT, "%.2f", row.totalWorkSeconds() / 3600.0)
                       : "")
               .append(',')
               .append(row.sessionCount())
               .append('\n');
        }

        return out.toString();
    }

    // ================= SHARED LOADING =================

    private CompanyData loadCompanyData(
            Long adminId,
            LocalDate from,
            LocalDate to,
            LocalDate today,
            Instant now) {

        List<Employee> employees = employeeService.getEmployeesByAdmin(adminId).stream()
                .filter(e -> "APPROVED".equalsIgnoreCase(e.getAccountStatus()))
                .toList();

        List<AttendanceSession> sessions = sessionRepository.findInCompanyOfBetween(adminId, from, to);
        List<Attendance> records = attendanceRepository
                .findByEmployeeInCompanyOfAndAttendanceDateBetween(adminId, from, to);
        List<LeaveManagement> leaves = leaveRepository
                .findByEmployeeInCompanyOfAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        adminId, to, from);
        List<WfhRequest> wfh = wfhRepository.findApprovedInCompanyOfBetween(adminId, from, to);
        List<LocalDate> holidays = holidayRepository
                .findByCompanyOfAndHolidayDateBetween(adminId, from, to).stream()
                .map(Holiday::getHolidayDate)
                .toList();

        AttendanceLedger ledger = new AttendanceLedger(sessions, records, leaves, wfh, holidays, today, now);

        return new CompanyData(employees, ledger, datesBetween(from, to));
    }

    private AdminAttendanceRowDTO toRow(Employee employee, AttendanceLedger.DayFacts facts) {

        return new AdminAttendanceRowDTO(
                employee.getEmployeeId(),
                employee.getEmployeeCode(),
                employee.getEmployeeName(),
                employee.getRole(),
                facts.date(),
                facts.firstLoginAt(),
                facts.lastLogoutAt(),
                facts.totalWorkSeconds(),
                facts.sessionCount(),
                facts.status(),
                facts.workingDay(),
                facts.workMode(),
                facts.wfhApproved(),
                facts.onLeave(),
                facts.openSessionId(),
                facts.openSince(),
                facts.openSeconds(),
                facts.recordId());
    }

    private AdminAttendanceSummaryDTO.RangeTotals totals(
            AttendanceLedger ledger,
            List<Employee> employees,
            List<LocalDate> dates) {

        long sessions = 0;
        long seconds = 0;
        long daysWorked = 0;

        for (LocalDate date : dates) {
            for (Employee employee : employees) {
                AttendanceLedger.DayFacts facts = ledger.day(employee.getEmployeeId(), date);
                sessions += facts.sessionCount();
                seconds += facts.totalWorkSeconds();
                if (isWorkedStatus(facts.status())) {
                    daysWorked++;
                }
            }
        }

        LocalDate from = dates.isEmpty() ? null : dates.get(0);
        LocalDate to = dates.isEmpty() ? null : dates.get(dates.size() - 1);

        return new AdminAttendanceSummaryDTO.RangeTotals(from, to, sessions, seconds, daysWorked);
    }

    private AdminAttendanceSummaryDTO.Today countToday(
            AttendanceLedger ledger,
            List<Employee> employees,
            LocalDate today) {

        long loggedIn = 0;
        long loggedOut = 0;
        long notLoggedIn = 0;
        long incomplete = 0;
        long onLeave = 0;
        long workedSeconds = 0;

        for (Employee employee : employees) {
            AttendanceLedger.DayFacts facts = ledger.day(employee.getEmployeeId(), today);
            switch (facts.status()) {
                case AttendanceDayStatus.LOGGED_IN -> {
                    loggedIn++;
                    workedSeconds += facts.totalWorkSeconds() + facts.openSeconds();
                }
                case AttendanceDayStatus.LOGGED_OUT, AttendanceDayStatus.PRESENT -> {
                    loggedOut++;
                    workedSeconds += facts.totalWorkSeconds();
                }
                case AttendanceDayStatus.NOT_LOGGED_IN -> notLoggedIn++;
                case AttendanceDayStatus.INCOMPLETE_SESSION -> incomplete++;
                case AttendanceDayStatus.ON_LEAVE -> onLeave++;
                default -> {
                }
            }
        }

        long activeEmployees = Math.max(1L, employees.size() - onLeave);
        int presencePercent = (int) Math.round(100.0 * (loggedIn + loggedOut) / activeEmployees);

        return new AdminAttendanceSummaryDTO.Today(
                employees.size(), loggedIn, loggedOut, notLoggedIn, incomplete, onLeave,
                presencePercent, workedSeconds);
    }

    private static boolean isWorkedStatus(String status) {
        return AttendanceDayStatus.LOGGED_IN.equals(status)
                || AttendanceDayStatus.LOGGED_OUT.equals(status)
                || AttendanceDayStatus.PRESENT.equals(status);
    }

    private static List<LocalDate> datesBetween(LocalDate from, LocalDate to) {

        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    private String ist(ZoneId zone, Instant instant) {
        return instant == null ? "" : IST_TIME.withZone(zone).format(instant);
    }

    /** CSV safe cell: quote when needed, and stop spreadsheet formula injection. */
    private static String escape(String value) {

        if (value == null) {
            return "";
        }

        String safe = value;
        if (!safe.isEmpty() && (safe.charAt(0) == '=' || safe.charAt(0) == '+'
                || safe.charAt(0) == '-' || safe.charAt(0) == '@')) {
            safe = "'" + safe;
        }

        return safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0
                ? '"' + safe.replace("\"", "\"\"") + '"'
                : safe;
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
    }

    private record CompanyData(List<Employee> employees, AttendanceLedger ledger, List<LocalDate> days) {
    }
}