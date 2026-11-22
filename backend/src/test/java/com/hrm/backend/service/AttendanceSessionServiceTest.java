package com.hrm.backend.service;

import com.hrm.backend.dto.EmployeeAttendanceHistoryDTO;
import com.hrm.backend.dto.EmployeeAttendanceTodayDTO;
import com.hrm.backend.entity.Attendance;
import com.hrm.backend.entity.AttendanceSession;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.entity.WfhRequest;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.AttendanceSessionRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.HolidayRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.WfhRequestRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login / logout rules.
 *
 * The service is driven with mocked persistence so the rules are pinned without a
 * database: one open session per employee, server-side timestamps only, durations that
 * add up across sessions, and a session left open on an earlier day that is flagged
 * instead of being given an invented logout time.
 */
class AttendanceSessionServiceTest {

    private static final Long EMPLOYEE_ID = 7L;
    private static final String EMAIL = "qa@hrm.local";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21); // Saturday
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final Instant NOW = Instant.parse("2026-11-21T04:30:00Z"); // 10:00 IST
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private AttendanceSessionRepository sessionRepository;
    private AttendanceRepository attendanceRepository;
    private EmployeeRepository employeeRepository;
    private WfhRequestRepository wfhRepository;
    private LeaveManagementRepository leaveRepository;
    private EmployeeService employeeService;
    private CompanyTimeService companyTime;
    private AttendanceSessionService service;

    private Employee employee;
    private List<AttendanceSession> storedSessions;
    private List<Attendance> storedRecords;

    @BeforeEach
    void setUp() {

        sessionRepository = mock(AttendanceSessionRepository.class);
        attendanceRepository = mock(AttendanceRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        wfhRepository = mock(WfhRequestRepository.class);
        leaveRepository = mock(LeaveManagementRepository.class);
        employeeService = mock(EmployeeService.class);
        companyTime = mock(CompanyTimeService.class);

        service = new AttendanceSessionService(
                sessionRepository,
                attendanceRepository,
                employeeRepository,
                wfhRepository,
                leaveRepository,
                mock(HolidayRepository.class),
                employeeService,
                companyTime,
                mock(AuditLogService.class));

        employee = new Employee();
        employee.setEmployeeId(EMPLOYEE_ID);
        employee.setEmployeeName("Attendance QA");
        employee.setEmployeeCode("QA-1");
        employee.setEmail(EMAIL);
        employee.setRole("Recruiter");
        employee.setAccountStatus("APPROVED");

        storedSessions = new ArrayList<>();
        storedRecords = new ArrayList<>();

        when(companyTime.todayForEmployee(any())).thenReturn(TODAY);
        when(companyTime.now()).thenReturn(NOW);
        when(companyTime.zoneForCompany(any())).thenReturn(IST);

        when(employeeService.getEmployeeIdByEmail(EMAIL)).thenReturn(EMPLOYEE_ID);
        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        when(sessionRepository.saveAndFlush(any(AttendanceSession.class))).thenAnswer(inv -> {
            AttendanceSession row = inv.getArgument(0);
            if (row.getSessionId() == null) {
                row.setSessionId((long) storedSessions.size() + 1);
                storedSessions.add(row);
            }
            return row;
        });

        when(attendanceRepository.saveAndFlush(any(Attendance.class))).thenAnswer(inv -> {
            Attendance row = inv.getArgument(0);
            if (row.getAttendanceId() == null) {
                row.setAttendanceId((long) storedRecords.size() + 1);
                storedRecords.add(row);
            }
            return row;
        });

        when(sessionRepository.findForEmployeeBetween(eq(EMPLOYEE_ID), any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(1);
            LocalDate to = inv.getArgument(2);
            return storedSessions.stream()
                    .filter(s -> !s.getAttendanceDate().isBefore(from) && !s.getAttendanceDate().isAfter(to))
                    .toList();
        });

        when(attendanceRepository.findByEmployeeEmployeeIdAndAttendanceDateBetween(
                eq(EMPLOYEE_ID), any(), any())).thenAnswer(inv -> {
                    LocalDate from = inv.getArgument(1);
                    LocalDate to = inv.getArgument(2);
                    return storedRecords.stream()
                            .filter(r -> !r.getAttendanceDate().isBefore(from) && !r.getAttendanceDate().isAfter(to))
                            .toList();
                });

        when(wfhRepository.findOverlapping(eq(EMPLOYEE_ID), any(), any(), any())).thenReturn(List.of());
        when(wfhRepository.findApprovedCovering(eq(EMPLOYEE_ID), any())).thenReturn(List.of());
        when(leaveRepository.findByEmployeeEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(EMPLOYEE_ID), any(), any())).thenReturn(List.of());
    }

    // ---------- helpers ----------

    private AttendanceSession store(String status, LocalDate date, Instant login, Instant logout) {
        AttendanceSession row = new AttendanceSession();
        row.setSessionId((long) storedSessions.size() + 1);
        row.setEmployee(employee);
        row.setAttendanceDate(date);
        row.setLoginAt(login);
        row.setLogoutAt(logout);
        row.setStatus(status);
        if (login != null && logout != null) {
            row.setDurationSeconds(Duration.between(login, logout).getSeconds());
        }
        storedSessions.add(row);
        return row;
    }

    private void openSessionIs(AttendanceSession row) {
        when(sessionRepository.findOneByEmployeeAndStatus(eq(EMPLOYEE_ID), eq("ACTIVE")))
                .thenReturn(Optional.ofNullable(row));
    }

    private void noOpenSession() {
        when(sessionRepository.findOneByEmployeeAndStatus(eq(EMPLOYEE_ID), eq("ACTIVE")))
                .thenReturn(Optional.empty());
    }

    private void noStoredRecordForToday() {
        when(attendanceRepository.findByEmployeeEmployeeIdAndAttendanceDate(eq(EMPLOYEE_ID), eq(TODAY)))
                .thenReturn(Optional.empty());
    }

    // ---------- login ----------

    @Test
    void loginOpensASessionWithTheServerTimestampAndCreatesThePresentRecord() {

        noOpenSession();
        noStoredRecordForToday();

        EmployeeAttendanceTodayDTO view = service.login(EMAIL);

        assertEquals(AttendanceDayStatus.LOGGED_IN, view.status());
        assertTrue(view.loggedIn());
        assertEquals(1, view.sessionCount());
        assertEquals(NOW, view.firstLoginAt(), "the login time is the server clock, not the request");
        assertEquals("OFFICE", view.workMode());
        assertEquals(1, storedSessions.size());
        assertEquals("ACTIVE", storedSessions.get(0).getStatus());

        verify(attendanceRepository).saveAndFlush(any(Attendance.class));
        assertEquals("PRESENT", storedRecords.get(0).getStatus());
        assertNotNull(storedRecords.get(0).getMarkedAt());
    }

    @Test
    void loginWithAnApprovedWfhRequestRecordsWfh() {

        WfhRequest wfh = new WfhRequest();
        wfh.setEmployee(employee);
        wfh.setStatus("APPROVED");
        wfh.setStartDate(TODAY);
        wfh.setEndDate(TODAY);

        when(wfhRepository.findApprovedCovering(EMPLOYEE_ID, TODAY)).thenReturn(List.of(wfh));
        when(wfhRepository.findOverlapping(eq(EMPLOYEE_ID), any(), any(), any())).thenReturn(List.of(wfh));
        noOpenSession();
        noStoredRecordForToday();

        EmployeeAttendanceTodayDTO view = service.login(EMAIL);

        assertEquals("WFH", view.workMode());
        assertTrue(view.wfhApproved());
        assertEquals("WFH", storedRecords.get(0).getWorkMode());
        assertEquals(wfh, storedRecords.get(0).getWfhRequest());
    }

    @Test
    void duplicateLoginIsRejected() {

        openSessionIs(store("ACTIVE", TODAY, NOW.minusSeconds(600), null));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.login(EMAIL));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(1, storedSessions.size(), "no second session is created");
    }

    @Test
    void aRacingLoginRejectedByTheDatabaseIndexIsReportedAsConflict() {

        noOpenSession();
        when(sessionRepository.saveAndFlush(any(AttendanceSession.class)))
                .thenThrow(new DataIntegrityViolationException("uk_attendance_sessions_one_active"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.login(EMAIL));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void aSessionLeftOpenYesterdayIsFlaggedAndNotGivenAnInventedLogout() {

        AttendanceSession stale = store("ACTIVE", YESTERDAY, NOW.minus(Duration.ofHours(26)), null);
        openSessionIs(stale);
        noStoredRecordForToday();

        EmployeeAttendanceTodayDTO view = service.login(EMAIL);

        assertEquals("INCOMPLETE", stale.getStatus());
        assertNull(stale.getLogoutAt(), "an unrecorded logout time must stay empty");
        assertNull(stale.getDurationSeconds());
        assertNotNull(view.notice());
        assertTrue(view.notice().contains("left open"));

        assertEquals(2, storedSessions.size(), "a new session is opened for today");
        assertEquals(AttendanceDayStatus.LOGGED_IN, view.status());
    }

    // ---------- logout ----------

    @Test
    void logoutClosesTheSessionAndStoresTheDuration() {

        Instant loginAt = NOW.minus(Duration.ofHours(2).plusMinutes(30));
        AttendanceSession open = store("ACTIVE", TODAY, loginAt, null);
        openSessionIs(open);

        EmployeeAttendanceTodayDTO view = service.logout(EMAIL);

        assertEquals("CLOSED", open.getStatus());
        assertEquals(NOW, open.getLogoutAt());
        assertEquals(9_000L, open.getDurationSeconds());
        assertEquals("EMPLOYEE", open.getClosedBy());
        assertEquals(AttendanceDayStatus.LOGGED_OUT, view.status());
        assertFalse(view.loggedIn());
        assertEquals(9_000L, view.totalWorkSeconds());
    }

    @Test
    void logoutWithoutAnOpenSessionIsRejected() {

        noOpenSession();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.logout(EMAIL));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(storedSessions.isEmpty());
        verify(sessionRepository, never()).saveAndFlush(any(AttendanceSession.class));
    }

    @Test
    void logoutOfASessionLeftOpenOnAnEarlierDayFlagsItInsteadOfClosingIt() {

        AttendanceSession stale = store("ACTIVE", YESTERDAY, NOW.minus(Duration.ofHours(20)), null);
        openSessionIs(stale);

        EmployeeAttendanceTodayDTO view = service.logout(EMAIL);

        assertEquals("INCOMPLETE", stale.getStatus());
        assertNull(stale.getLogoutAt());
        assertNull(stale.getDurationSeconds());
        assertNotNull(view.notice());
        assertEquals(AttendanceDayStatus.NOT_LOGGED_IN, view.status(),
                "today has no session of its own, so it is not reported as worked");
    }

    @Test
    void aClockThatWentBackwardsNeverStoresANegativeDuration() {

        AttendanceSession open = store("ACTIVE", TODAY, NOW.plusSeconds(120), null);
        openSessionIs(open);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.logout(EMAIL));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("ACTIVE", open.getStatus());
        assertNull(open.getDurationSeconds());
    }

    // ---------- multiple sessions ----------

    @Test
    void severalSessionsInOneDayAddUpWithoutDoubleCounting() {

        Instant first = NOW.minus(Duration.ofMinutes(60));
        Instant second = NOW.minus(Duration.ofMinutes(30));

        store("CLOSED", TODAY, first, first.plus(Duration.ofMinutes(60)));      // 3600s
        store("CLOSED", TODAY, second, second.plus(Duration.ofMinutes(90)));    // 5400s
        noOpenSession();

        EmployeeAttendanceTodayDTO view = service.today(EMAIL);

        assertEquals(2, view.sessionCount());
        assertEquals(9_000L, view.totalWorkSeconds());
        assertEquals(0L, view.activeSeconds());
        assertEquals(first, view.firstLoginAt());
        assertEquals(second.plus(Duration.ofMinutes(90)), view.lastLogoutAt());
        assertEquals(AttendanceDayStatus.LOGGED_OUT, view.status());
    }

    @Test
    void anOpenSessionReportsItsElapsedSecondsSeparately() {

        Instant startedAt = NOW.minus(Duration.ofMinutes(25));
        store("CLOSED", TODAY, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1))); // 3600s
        AttendanceSession open = store("ACTIVE", TODAY, startedAt, null);
        openSessionIs(open);

        EmployeeAttendanceTodayDTO view = service.today(EMAIL);

        assertTrue(view.loggedIn());
        assertEquals(1_500L, view.activeSeconds());
        assertEquals(startedAt, view.activeSince());
        assertEquals(3_600L, view.totalWorkSeconds(),
                "completed sessions are the stored total; the open one is reported live");
    }

    // ---------- history ----------

    @Test
    void historyKeepsLegacyPresentRecordsAndCountsDaysWorked() {

        Attendance legacy = new Attendance();
        legacy.setAttendanceId(99L);
        legacy.setEmployee(employee);
        legacy.setAttendanceDate(YESTERDAY);
        legacy.setStatus("PRESENT");
        legacy.setWorkMode("OFFICE");
        storedRecords.add(legacy);
        noOpenSession();

        EmployeeAttendanceHistoryDTO history = service.history(EMAIL, YESTERDAY, TODAY);

        assertEquals(2, history.days().size());
        assertEquals(AttendanceDayStatus.PRESENT, history.days().get(0).status());
        assertEquals(AttendanceDayStatus.NOT_LOGGED_IN, history.days().get(1).status());
        assertEquals(1, history.daysWorked());
    }

    @Test
    void historyCountsEverySessionOfEveryDay() {

        Instant login = NOW.minus(Duration.ofHours(3));
        store("CLOSED", YESTERDAY, login, login.plus(Duration.ofMinutes(45)));
        store("CLOSED", TODAY, NOW.minus(Duration.ofMinutes(20)), NOW.minus(Duration.ofMinutes(10)));
        noOpenSession();

        EmployeeAttendanceHistoryDTO history = service.history(EMAIL, YESTERDAY, TODAY);

        assertEquals(2, history.sessionCount());
        assertEquals(2_700L + 600L, history.totalWorkSeconds());
        assertEquals(2, history.daysWorked());
        assertEquals(1, history.days().get(0).sessionCount());
        assertEquals(1, history.days().get(1).sessionCount());
    }

    @Test
    void anApprovedLeaveShowsAsOnLeaveAndNotAsAbsent() {

        LeaveManagement leave = new LeaveManagement();
        leave.setEmployee(employee);
        leave.setStatus("APPROVED");
        leave.setStartDate(YESTERDAY);
        leave.setEndDate(YESTERDAY);

        when(leaveRepository.findByEmployeeEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(EMPLOYEE_ID), any(), any())).thenReturn(List.of(leave));
        noOpenSession();

        EmployeeAttendanceHistoryDTO history = service.history(EMAIL, YESTERDAY, YESTERDAY);

        assertEquals(AttendanceDayStatus.ON_LEAVE, history.days().get(0).status());
        assertEquals(0, history.daysWorked());
    }

    @Test
    void historyRejectsAnInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> service.history(EMAIL, TODAY, YESTERDAY));
    }

    @Test
    void loginFlagsTheStaleSessionOnlyOnce() {

        // Two logins in a row: the first sees nothing open, the second sees today's session.
        noOpenSession();
        noStoredRecordForToday();
        service.login(EMAIL);

        openSessionIs(storedSessions.get(0));

        assertThrows(ResponseStatusException.class, () -> service.login(EMAIL));
        verify(sessionRepository, times(1)).saveAndFlush(any(AttendanceSession.class));
    }
}
