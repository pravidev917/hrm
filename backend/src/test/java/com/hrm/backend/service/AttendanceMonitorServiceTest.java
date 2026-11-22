package com.hrm.backend.service;

import com.hrm.backend.dto.AdminAttendanceRowDTO;
import com.hrm.backend.dto.AdminAttendanceSummaryDTO;
import com.hrm.backend.entity.AttendanceSession;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.AttendanceSessionRepository;
import com.hrm.backend.repository.HolidayRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.WfhRequestRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The administrator monitor: one row per employee per day, the live list and the
 * daily / weekly / monthly totals - all derived from the session log.
 */
class AttendanceMonitorServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long COMPANY_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21); // Saturday
    private static final LocalDate MONDAY = TODAY.minusDays(5);
    private static final Instant NOW = Instant.parse("2026-11-21T04:30:00Z"); // 10:00 IST
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private AttendanceSessionRepository sessionRepository;
    private AttendanceRepository attendanceRepository;
    private WfhRequestRepository wfhRepository;
    private LeaveManagementRepository leaveRepository;
    private HolidayRepository holidayRepository;
    private EmployeeService employeeService;
    private AdminService adminService;
    private CompanyScopeService companyScope;
    private CompanyTimeService companyTime;
    private AttendanceSessionService sessionService;
    private AttendanceMonitorService service;

    private Employee loggedOutEmployee;
    private Employee idleEmployee;

    @BeforeEach
    void setUp() {

        sessionRepository = mock(AttendanceSessionRepository.class);
        attendanceRepository = mock(AttendanceRepository.class);
        wfhRepository = mock(WfhRequestRepository.class);
        leaveRepository = mock(LeaveManagementRepository.class);
        holidayRepository = mock(HolidayRepository.class);
        employeeService = mock(EmployeeService.class);
        adminService = mock(AdminService.class);
        companyScope = mock(CompanyScopeService.class);
        companyTime = mock(CompanyTimeService.class);
        sessionService = mock(AttendanceSessionService.class);

        service = new AttendanceMonitorService(
                sessionRepository,
                attendanceRepository,
                wfhRepository,
                leaveRepository,
                holidayRepository,
                employeeService,
                adminService,
                companyScope,
                companyTime,
                sessionService);

        loggedOutEmployee = employee(11L, "Asha Menon", "QA-11", "Recruiter");
        idleEmployee = employee(12L, "Vikram Rao", "QA-12", "Team Lead");

        when(adminService.requireCompanyId(ADMIN_ID)).thenReturn(COMPANY_ID);
        when(companyTime.todayForCompany(COMPANY_ID)).thenReturn(TODAY);
        when(companyTime.now()).thenReturn(NOW);
        when(companyTime.zoneForCompany(COMPANY_ID)).thenReturn(IST);

        when(employeeService.getEmployeesByAdmin(ADMIN_ID)).thenReturn(List.of(loggedOutEmployee, idleEmployee));
        when(attendanceRepository.findByEmployeeInCompanyOfAndAttendanceDateBetween(eq(ADMIN_ID), any(), any()))
                .thenReturn(List.of());
        when(leaveRepository.findByEmployeeInCompanyOfAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(ADMIN_ID), any(), any())).thenReturn(List.of());
        when(wfhRepository.findApprovedInCompanyOfBetween(eq(ADMIN_ID), any(), any())).thenReturn(List.of());
        when(holidayRepository.findByCompanyOfAndHolidayDateBetween(eq(ADMIN_ID), any(), any()))
                .thenReturn(List.of());
    }

    private Employee employee(Long id, String name, String code, String role) {
        Employee employee = new Employee();
        employee.setEmployeeId(id);
        employee.setEmployeeName(name);
        employee.setEmployeeCode(code);
        employee.setRole(role);
        employee.setAccountStatus("APPROVED");
        return employee;
    }

    private AttendanceSession session(Employee employee, LocalDate date, Instant login, Instant logout, String status) {
        AttendanceSession row = new AttendanceSession();
        row.setSessionId(date.toEpochDay() + employee.getEmployeeId());
        row.setEmployee(employee);
        row.setAttendanceDate(date);
        row.setLoginAt(login);
        row.setLogoutAt(logout);
        row.setStatus(status);
        if (logout != null) {
            row.setDurationSeconds(Duration.between(login, logout).getSeconds());
        }
        return row;
    }

    private void sessionsAre(AttendanceSession... rows) {
        when(sessionRepository.findInCompanyOfBetween(eq(ADMIN_ID), any(), any()))
                .thenReturn(List.of(rows));
    }

    // ---------- rows ----------

    @Test
    void rowsShowWhoLoggingInWhoLoggedOutAndWhoNeverDid() {

        sessionsAre(session(loggedOutEmployee, TODAY,
                NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), "CLOSED"));

        List<AdminAttendanceRowDTO> rows = service.rows(ADMIN_ID, TODAY, TODAY, null, null, null, null);

        assertEquals(2, rows.size(), "one row per active employee for the day");

        AdminAttendanceRowDTO worked = rows.stream()
                .filter(r -> r.employeeId().equals(11L)).findFirst().orElseThrow();
        assertEquals(AttendanceDayStatus.LOGGED_OUT, worked.status());
        assertEquals(1, worked.sessionCount());
        assertEquals(3_600L, worked.totalWorkSeconds());
        assertEquals("Recruiter", worked.designation());
        assertTrue(worked.workingDay(), "Saturday is a working day");

        AdminAttendanceRowDTO idle = rows.stream()
                .filter(r -> r.employeeId().equals(12L)).findFirst().orElseThrow();
        assertEquals(AttendanceDayStatus.NOT_LOGGED_IN, idle.status(),
                "a working day that is not over cannot be an absence yet");
        assertEquals(0, idle.sessionCount());
    }

    @Test
    void aPastWorkingDayWithoutAnySessionIsAbsent() {

        sessionsAre();

        List<AdminAttendanceRowDTO> rows = service.rows(ADMIN_ID, MONDAY, MONDAY, null, null, null, null);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> AttendanceDayStatus.ABSENT.equals(r.status())));
    }

    @Test
    void aSundayIsReportedAsWeeklyOff() {

        LocalDate sunday = TODAY.plusDays(1);
        sessionsAre();

        List<AdminAttendanceRowDTO> rows = service.rows(ADMIN_ID, sunday, sunday, null, null, null, null);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> AttendanceDayStatus.WEEKLY_OFF.equals(r.status())));
        assertFalse(rows.get(0).workingDay());
    }

    @Test
    void filtersNarrowByStatusDesignationEmployeeAndSearch() {

        sessionsAre(session(loggedOutEmployee, TODAY,
                NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), "CLOSED"));

        assertEquals(1, service.rows(ADMIN_ID, TODAY, TODAY, null, null, "LOGGED_OUT", null).size());
        assertEquals(1, service.rows(ADMIN_ID, TODAY, TODAY, null, "Team Lead", null, null).size());
        assertEquals(1, service.rows(ADMIN_ID, TODAY, TODAY, 11L, null, null, null).size());
        assertEquals(1, service.rows(ADMIN_ID, TODAY, TODAY, null, null, null, "asha").size());
        assertEquals(0, service.rows(ADMIN_ID, TODAY, TODAY, null, null, null, "nobody").size());
    }

    @Test
    void aRangeReturnsOneRowPerEmployeePerDay() {

        sessionsAre();

        List<AdminAttendanceRowDTO> rows = service.rows(ADMIN_ID, MONDAY, TODAY, null, null, null, null);

        assertEquals(12, rows.size(), "2 employees x 6 days (Mon..Sat)");
    }

    // ---------- active ----------

    @Test
    void activeListsOpenSessionsAndFlagsOnesLeftOpenOnAnEarlierDay() {

        AttendanceSession openNow = session(loggedOutEmployee, TODAY, NOW.minus(Duration.ofMinutes(20)), null, "ACTIVE");
        AttendanceSession stale = session(idleEmployee, TODAY.minusDays(2), NOW.minus(Duration.ofDays(2)), null, "ACTIVE");

        when(sessionRepository.findActiveInCompanyOf(ADMIN_ID)).thenReturn(List.of(stale, openNow));
        when(sessionRepository.findInCompanyOfBetween(eq(ADMIN_ID), any(), any()))
                .thenReturn(List.of(stale, openNow));

        List<AdminAttendanceRowDTO> rows = service.active(ADMIN_ID);

        assertEquals(2, rows.size());
        AdminAttendanceRowDTO live = rows.stream()
                .filter(r -> r.employeeId().equals(11L)).findFirst().orElseThrow();
        assertEquals(AttendanceDayStatus.LOGGED_IN, live.status());
        assertEquals(1_200L, live.openSeconds());

        AdminAttendanceRowDTO forgotten = rows.stream()
                .filter(r -> r.employeeId().equals(12L)).findFirst().orElseThrow();
        assertEquals(AttendanceDayStatus.INCOMPLETE_SESSION, forgotten.status());
        assertEquals(0L, forgotten.openSeconds(), "days of elapsed time are not working hours");
        assertEquals(TODAY.minusDays(2), forgotten.attendanceDate());
    }

    // ---------- summary ----------

    @Test
    void summaryReportsDailyWeeklyAndMonthlyTotals() {

        sessionsAre(
                session(loggedOutEmployee, TODAY, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), "CLOSED"),
                session(loggedOutEmployee, MONDAY, NOW.minus(Duration.ofDays(5).plus(Duration.ofHours(2))),
                        NOW.minus(Duration.ofDays(5).plus(Duration.ofHours(1))), "CLOSED"));

        AdminAttendanceSummaryDTO summary = service.summary(ADMIN_ID, MONDAY, TODAY);

        assertEquals(MONDAY, summary.from());
        assertEquals(TODAY, summary.to());
        assertEquals(2, summary.range().sessionCount());
        assertEquals(7_200L, summary.range().totalWorkSeconds());

        // This week starts on Monday the 16th, so both sessions are inside it.
        assertEquals(2, summary.weekly().sessionCount());
        assertEquals(7_200L, summary.monthly().totalWorkSeconds());

        assertEquals(2, summary.todayCounts().activeEmployees());
        assertEquals(1, summary.todayCounts().loggedOut());
        assertEquals(1, summary.todayCounts().notLoggedIn());
        assertEquals(50, summary.todayCounts().presencePercent());

        assertEquals(6, summary.daily().size());
        assertEquals(TODAY, summary.daily().get(5).date());
        assertEquals(1, summary.daily().get(5).sessionCount());

        AdminAttendanceSummaryDTO.EmployeeTotal asha = summary.employees().stream()
                .filter(e -> e.employeeId().equals(11L)).findFirst().orElseThrow();
        assertEquals(2, asha.daysWorked());
        assertEquals(2, asha.sessionCount());
        assertEquals(7_200L, asha.totalWorkSeconds());
    }

    // ---------- export ----------

    @Test
    void csvHasTheSameRowsWithIstTimesAndSafeCells() {

        sessionsAre(session(loggedOutEmployee, TODAY,
                NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), "CLOSED"));

        String csv = service.csv(ADMIN_ID, TODAY, TODAY, null, null, null, null);

        String[] lines = csv.split("\n");

        assertTrue(lines[0].contains("Employee ID,Employee Name,Designation,Attendance Date,Status"),
                "the header names every column the table shows");
        assertTrue(csv.contains("2026-11-21"));
        assertTrue(csv.contains("21 Nov 2026 08:00"), "login at 02:30Z is written as 08:00 IST");
        assertTrue(csv.contains("21 Nov 2026 09:00"), "logout at 03:30Z is written as 09:00 IST");
        assertTrue(csv.contains("1.00"), "one hour is exported as decimal hours");
        assertEquals(3, lines.length, "header + one row per active employee");
        assertFalse(csv.contains(",,") && csv.contains("=cmd"), "no formula injection");
    }
}
