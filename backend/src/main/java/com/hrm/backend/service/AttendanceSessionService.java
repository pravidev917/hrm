package com.hrm.backend.service;

import com.hrm.backend.dto.AttendanceSessionDTO;
import com.hrm.backend.dto.EmployeeAttendanceHistoryDTO;
import com.hrm.backend.dto.EmployeeAttendanceTodayDTO;
import com.hrm.backend.entity.Attendance;
import com.hrm.backend.entity.AttendanceSession;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.Holiday;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.entity.WfhRequest;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.AttendanceSessionRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.HolidayRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.WfhRequestRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Employee attendance: Log In / Log Out and the read models built on top of them.
 *
 * The rules this class exists to keep:
 *   - the employee is always the authenticated one; no method takes an employee id
 *     from the request for the employee's own view;
 *   - timestamps come from the server clock (CompanyTimeService), never from the body;
 *   - one open session per employee, guarded by an employee row lock and by a partial
 *     unique index, so a double click or two tabs cannot start two sessions;
 *   - logging in creates/keeps today's PRESENT attendance record (work mode WFH only
 *     when an approved work-from-home request covers today);
 *   - a session left open on a previous day is flagged INCOMPLETE and its logout time
 *     stays empty: no invented working hours;
 *   - a duration is never negative; a clock that went backwards aborts the logout.
 */
@Service
public class AttendanceSessionService {

    static final int MAX_RANGE_DAYS = 366;

    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final WfhRequestRepository wfhRepository;
    private final LeaveManagementRepository leaveRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeService employeeService;
    private final CompanyTimeService companyTime;
    private final AuditLogService auditLog;

    public AttendanceSessionService(
            AttendanceSessionRepository sessionRepository,
            AttendanceRepository attendanceRepository,
            EmployeeRepository employeeRepository,
            WfhRequestRepository wfhRepository,
            LeaveManagementRepository leaveRepository,
            HolidayRepository holidayRepository,
            EmployeeService employeeService,
            CompanyTimeService companyTime,
            AuditLogService auditLog) {

        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.wfhRepository = wfhRepository;
        this.leaveRepository = leaveRepository;
        this.holidayRepository = holidayRepository;
        this.employeeService = employeeService;
        this.companyTime = companyTime;
        this.auditLog = auditLog;
    }

    // ================= EMPLOYEE =================

    /**
     * Opens a new session for the authenticated employee.
     *
     * The employee row is locked first, so two simultaneous requests queue instead of
     * racing; the partial unique index in the database is the second line of defence.
     */
    @Transactional
    public EmployeeAttendanceTodayDTO login(String authenticatedEmail) {

        Employee employee = lockedEmployee(authenticatedEmail);
        Long employeeId = employee.getEmployeeId();
        LocalDate today = companyTime.todayForEmployee(employee);
        Instant now = companyTime.now();

        String notice = null;

        Optional<AttendanceSession> open =
                sessionRepository.findOneByEmployeeAndStatus(employeeId, AttendanceSession.STATUS_ACTIVE);

        if (open.isPresent()) {

            AttendanceSession previous = open.get();

            if (!previous.getAttendanceDate().isBefore(today)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You are already logged in. Log out before starting a new session.");
            }

            // Left open on an earlier day: flag it, never invent a logout time.
            previous.setStatus(AttendanceSession.STATUS_INCOMPLETE);
            previous.setClosedBy(AttendanceSession.CLOSED_BY_SYSTEM);
            previous.setUpdatedAt(now);
            sessionRepository.saveAndFlush(previous);

            notice = "Your session of " + DAY_FORMAT.format(previous.getAttendanceDate())
                    + " was left open and has been flagged as incomplete.";

            auditLog.employee(employee, "ATTENDANCE_SESSION_INCOMPLETE", "ATTENDANCE_SESSION",
                    previous.getSessionId(),
                    "Session of " + previous.getAttendanceDate()
                            + " left open automatically before a new login");
        }

        AttendanceSession session = new AttendanceSession();
        session.setEmployee(employee);
        session.setAttendanceDate(today);
        session.setLoginAt(now);
        session.setStatus(AttendanceSession.STATUS_ACTIVE);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);

        try {
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            // The database's one-active-session index rejected a racing request.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You are already logged in. Log out before starting a new session.");
        }

        ensurePresentRecord(employee, today, now);

        auditLog.employee(employee, "ATTENDANCE_LOGIN", "ATTENDANCE_SESSION", session.getSessionId(),
                "Logged in for " + today);

        return today(employee, today, now, notice);
    }

    /** Closes the open session with the server's own logout time and duration. */
    @Transactional
    public EmployeeAttendanceTodayDTO logout(String authenticatedEmail) {

        Employee employee = lockedEmployee(authenticatedEmail);
        Long employeeId = employee.getEmployeeId();
        LocalDate today = companyTime.todayForEmployee(employee);
        Instant now = companyTime.now();

        AttendanceSession session = sessionRepository
                .findOneByEmployeeAndStatus(employeeId, AttendanceSession.STATUS_ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "You are not logged in, so there is nothing to log out of."));

        if (session.getAttendanceDate().isBefore(today)) {
            // The day is over. Close the state, not the timestamp: nothing is invented.
            session.setStatus(AttendanceSession.STATUS_INCOMPLETE);
            session.setClosedBy(AttendanceSession.CLOSED_BY_SYSTEM);
            session.setUpdatedAt(now);
            sessionRepository.saveAndFlush(session);

            auditLog.employee(employee, "ATTENDANCE_SESSION_INCOMPLETE", "ATTENDANCE_SESSION",
                    session.getSessionId(),
                    "Logout attempted on " + today + " for an open session of "
                            + session.getAttendanceDate());

            return today(employee, today, now,
                    "Your session of " + DAY_FORMAT.format(session.getAttendanceDate())
                            + " was left open, so it has been flagged as incomplete. Log in to start today's session.");
        }

        long seconds = Duration.between(session.getLoginAt(), now).getSeconds();

        if (seconds < 0) {
            // Never store a negative duration: refuse instead of writing nonsense.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The server clock is inconsistent, so this logout was not saved. Try again.");
        }

        session.setLogoutAt(now);
        session.setDurationSeconds(seconds);
        session.setStatus(AttendanceSession.STATUS_CLOSED);
        session.setClosedBy(AttendanceSession.CLOSED_BY_EMPLOYEE);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);

        auditLog.employee(employee, "ATTENDANCE_LOGOUT", "ATTENDANCE_SESSION", session.getSessionId(),
                "Logged out for " + session.getAttendanceDate() + " after " + seconds + "s");

        return today(employee, today, now, null);
    }

    /** Same view for a caller that already holds the employee (e.g. the dashboard). */
    @Transactional(readOnly = true)
    public EmployeeAttendanceTodayDTO todayFor(Employee employee) {
        LocalDate today = companyTime.todayForEmployee(employee);
        return today(employee, today, companyTime.now(), null);
    }

    @Transactional(readOnly = true)
    public EmployeeAttendanceTodayDTO today(String authenticatedEmail) {

        Employee employee = currentEmployee(authenticatedEmail);
        LocalDate today = companyTime.todayForEmployee(employee);
        return today(employee, today, companyTime.now(), null);
    }

    /** The employee's own history. The window is capped so a page stays responsive. */
    @Transactional(readOnly = true)
    public EmployeeAttendanceHistoryDTO history(
            String authenticatedEmail,
            LocalDate from,
            LocalDate to) {

        Employee employee = currentEmployee(authenticatedEmail);
        LocalDate today = companyTime.todayForEmployee(employee);
        LocalDate start = from == null ? today.withDayOfMonth(1) : from;
        LocalDate end = to == null ? today : to;
        validateRange(start, end);

        return buildHistory(employee, start, end, today, companyTime.now());
    }

    // ================= SHARED READ MODEL =================

    AttendanceLedger ledgerForEmployee(
            Employee employee,
            LocalDate from,
            LocalDate to,
            LocalDate today,
            Instant now) {

        Long employeeId = employee.getEmployeeId();
        Long companyId = CompanyTimeService.companyIdOf(employee);

        List<AttendanceSession> sessions = sessionRepository.findForEmployeeBetween(employeeId, from, to);
        List<Attendance> records = attendanceRepository
                .findByEmployeeEmployeeIdAndAttendanceDateBetween(employeeId, from, to);
        List<LeaveManagement> leaves = leaveRepository
                .findByEmployeeEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employeeId, to, from);
        List<WfhRequest> wfh = wfhRepository
                .findOverlapping(employeeId, List.of("APPROVED"), from, to);
        List<LocalDate> holidays = companyId == null
                ? List.of()
                : holidayRepository.findForCompanyBetween(companyId, from, to).stream()
                        .map(Holiday::getHolidayDate)
                        .toList();

        return new AttendanceLedger(sessions, records, leaves, wfh, holidays, today, now);
    }

    EmployeeAttendanceHistoryDTO buildHistory(
            Employee employee,
            LocalDate from,
            LocalDate to,
            LocalDate today,
            Instant now) {

        AttendanceLedger ledger = ledgerForEmployee(employee, from, to, today, now);

        List<EmployeeAttendanceHistoryDTO.Day> days = new ArrayList<>();
        long totalSeconds = 0;
        long sessionCount = 0;
        long daysWorked = 0;
        long incomplete = 0;

        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {

            AttendanceLedger.DayFacts facts = ledger.day(employee.getEmployeeId(), cursor);

            totalSeconds += facts.totalWorkSeconds() + facts.openSeconds();
            sessionCount += facts.sessionCount();
            if (isWorked(facts.status())) {
                daysWorked++;
            }
            if (AttendanceDayStatus.INCOMPLETE_SESSION.equals(facts.status())) {
                incomplete++;
            }

            days.add(new EmployeeAttendanceHistoryDTO.Day(
                    facts.date(),
                    facts.status(),
                    facts.workingDay(),
                    facts.firstLoginAt(),
                    facts.lastLogoutAt(),
                    facts.totalWorkSeconds() + facts.openSeconds(),
                    facts.sessionCount(),
                    facts.workMode(),
                    sessions(facts.sessions(), employee)));

            cursor = cursor.plusDays(1);
        }

        return new EmployeeAttendanceHistoryDTO(
                employee.getEmployeeId(),
                employee.getEmployeeCode(),
                employee.getEmployeeName(),
                employee.getRole(),
                from,
                to,
                totalSeconds,
                sessionCount,
                daysWorked,
                incomplete,
                days);
    }

    // ================= INTERNALS =================

    private EmployeeAttendanceTodayDTO today(
            Employee employee,
            LocalDate today,
            Instant now,
            String notice) {

        AttendanceLedger ledger = ledgerForEmployee(employee, today, today, today, now);
        AttendanceLedger.DayFacts facts = ledger.day(employee.getEmployeeId(), today);

        boolean loggedIn = facts.openSessionId() != null;

        return new EmployeeAttendanceTodayDTO(
                employee.getEmployeeId(),
                employee.getEmployeeName(),
                employee.getEmployeeCode(),
                today,
                facts.status(),
                loggedIn,
                facts.wfhApproved(),
                facts.onLeave(),
                ledger.isWorkingDay(today),
                facts.workMode(),
                facts.firstLoginAt(),
                facts.lastLogoutAt(),
                facts.totalWorkSeconds(),
                loggedIn ? facts.openSeconds() : 0L,
                loggedIn ? facts.openSince() : null,
                facts.sessionCount(),
                sessions(facts.sessions(), employee),
                notice);
    }

    private List<AttendanceSessionDTO> sessions(List<AttendanceSession> rows, Employee employee) {

        List<AttendanceSessionDTO> list = new ArrayList<>(rows.size());

        for (AttendanceSession row : rows) {
            list.add(new AttendanceSessionDTO(
                    row.getSessionId(),
                    employee.getEmployeeId(),
                    employee.getEmployeeName(),
                    row.getAttendanceDate(),
                    row.getLoginAt(),
                    row.getLogoutAt(),
                    row.getDurationSeconds(),
                    row.getStatus(),
                    row.isActive()));
        }

        // Oldest first: a day reads top-down like the employee lived it.
        list.sort((a, b) -> a.loginAt().compareTo(b.loginAt()));
        return list;
    }

    /**
     * A login is proof of presence, so today's attendance record is created (or
     * corrected from ABSENT to PRESENT) as part of the same transaction.
     * The work mode is decided by the server from the approved WFH requests.
     */
    private void ensurePresentRecord(Employee employee, LocalDate date, Instant now) {

        List<WfhRequest> approved = wfhRepository.findApprovedCovering(employee.getEmployeeId(), date);
        WfhRequest wfh = approved.isEmpty() ? null : approved.get(0);
        String mode = wfh == null ? "OFFICE" : "WFH";

        ZoneId zone = companyTime.zoneForCompany(CompanyTimeService.companyIdOf(employee));
        LocalDateTime markedAt = LocalDateTime.ofInstant(now, zone);

        Optional<Attendance> existing = attendanceRepository
                .findByEmployeeEmployeeIdAndAttendanceDate(employee.getEmployeeId(), date);

        if (existing.isEmpty()) {
            Attendance record = new Attendance();
            record.setEmployee(employee);
            record.setAttendanceDate(date);
            record.setStatus("PRESENT");
            record.setWorkMode(mode);
            record.setMarkedAt(markedAt);
            record.setWfhRequest(wfh);
            attendanceRepository.saveAndFlush(record);
            return;
        }

        Attendance record = existing.get();

        if ("PRESENT".equalsIgnoreCase(record.getStatus())) {

            // A record that carries verified location evidence is history: leave it.
            boolean createdByLogin = record.getDistanceMeters() == null
                    && record.getWfhRequest() == null;

            if (!createdByLogin || mode.equals(record.getWorkMode())) {
                return;
            }

            // A WFH approval arrived after the first login: the login record follows it,
            // because a login record has no location evidence to contradict.
            record.setWorkMode(mode);
            record.setWfhRequest(wfh);
            record.setMarkedAt(markedAt);
            attendanceRepository.saveAndFlush(record);
            return;
        }

        record.setStatus("PRESENT");
        record.setWorkMode(mode);
        record.setMarkedAt(markedAt);
        record.setWfhRequest(wfh);
        attendanceRepository.saveAndFlush(record);

        auditLog.employee(employee, "ATTENDANCE_CORRECTED", "ATTENDANCE", record.getAttendanceId(),
                "Login turned " + date + " into PRESENT/" + mode);
    }

    private static boolean isWorked(String status) {
        return AttendanceDayStatus.LOGGED_IN.equals(status)
                || AttendanceDayStatus.LOGGED_OUT.equals(status)
                || AttendanceDayStatus.PRESENT.equals(status);
    }

    static void validateRange(LocalDate from, LocalDate to) {

        if (from == null || to == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("Select a range of at most " + MAX_RANGE_DAYS + " days");
        }
    }

    private Employee currentEmployee(String authenticatedEmail) {

        Long employeeId = employeeService.getEmployeeIdByEmail(authenticatedEmail);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        requireApproved(employee);
        return employee;
    }

    private Employee lockedEmployee(String authenticatedEmail) {

        Long employeeId = employeeService.getEmployeeIdByEmail(authenticatedEmail);

        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        requireApproved(employee);
        return employee;
    }

    private void requireApproved(Employee employee) {
        if (!"APPROVED".equalsIgnoreCase(employee.getAccountStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee account is not approved");
        }
    }
}
