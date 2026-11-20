package com.hrm.backend.service;

import com.hrm.backend.dto.AttendanceMarkRequest;
import com.hrm.backend.dto.AttendanceResponseDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Attendance;
import com.hrm.backend.entity.Company;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.OfficeLocation;
import com.hrm.backend.entity.WfhRequest;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.OfficeLocationRepository;
import com.hrm.backend.repository.WfhRequestRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Attendance verification rules.
 *
 * All decisions are made server-side: distance from the configured office,
 * accuracy, freshness and the approved-WFH check. The tests drive the service
 * with mocked persistence so the rules are checked without a database.
 */
class AttendanceServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 18);
    private static final Instant NOW = Instant.parse("2025-06-18T09:00:00Z");
    private static final long NOW_MILLIS = NOW.toEpochMilli();

    private AttendanceRepository attendanceRepository;
    private EmployeeRepository employeeRepository;
    private OfficeLocationRepository officeRepository;
    private WfhRequestRepository wfhRepository;
    private CompanyTimeService companyTime;
    private AuditLogService auditLog;
    private AttendanceService service;

    private Employee employee;

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        officeRepository = mock(OfficeLocationRepository.class);
        wfhRepository = mock(WfhRequestRepository.class);
        companyTime = mock(CompanyTimeService.class);
        auditLog = mock(AuditLogService.class);

        service = new AttendanceService(
                attendanceRepository,
                employeeRepository,
                officeRepository,
                wfhRepository,
                mock(EmployeeService.class),
                mock(AdminService.class),
                mock(CompanyScopeService.class),
                companyTime,
                auditLog,
                50d,
                120L);

        employee = employee(9L, 1L);

        when(employeeRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(employee));
        when(companyTime.todayForEmployee(employee)).thenReturn(TODAY);
        when(companyTime.now()).thenReturn(NOW);
        when(attendanceRepository.saveAndFlush(any(Attendance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void insideRadiusIsAcceptedAndRecordsOfficeMode() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.of(office(200)));

        // ~52 m north of the office.
        AttendanceResponseDTO result = service.markAttendance(
                9L, "PRESENT", location(12.972070, 77.594600, 10d, NOW_MILLIS));

        assertEquals("PRESENT", result.getStatus());
        assertEquals("OFFICE", result.getWorkMode());
        assertNotNull(result.getDistanceMeters());
        assertTrue(result.getDistanceMeters().doubleValue() > 0);
        assertTrue(result.getDistanceMeters().doubleValue() < 200);
        assertEquals(10d, result.getAccuracyMeters().doubleValue(), 0.01);
    }

    @Test
    void outsideRadiusIsRejectedWithDistanceGuidance() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.of(office(200)));

        // ~1.1 km north of the office.
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.markAttendance(
                        9L, "PRESENT", location(12.981600, 77.594600, 10d, NOW_MILLIS)));

        assertTrue(ex.getMessage().contains("allowed only within 200 m"));
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidCoordinateIsRejected() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.of(office(200)));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.markAttendance(
                        9L, "PRESENT", location(200d, 77.5946, 10d, NOW_MILLIS)));

        assertTrue(ex.getMessage().toLowerCase().contains("invalid"));
    }

    @Test
    void staleCaptureIsRejected() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.of(office(200)));

        long fiveMinutesAgo = NOW.minusSeconds(300).toEpochMilli();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.markAttendance(
                        9L, "PRESENT", location(12.972070, 77.594600, 10d, fiveMinutesAgo)));

        assertTrue(ex.getMessage().contains("stale"));
    }

    @Test
    void futureCaptureIsRejected() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.of(office(200)));

        long fiveMinutesAhead = NOW.plusSeconds(300).toEpochMilli();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.markAttendance(
                        9L, "PRESENT", location(12.972070, 77.594600, 10d, fiveMinutesAhead)));

        assertTrue(ex.getMessage().contains("future"));
    }

    @Test
    void inaccurateReadingIsRejected() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.of(office(200)));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.markAttendance(
                        9L, "PRESENT", location(12.972070, 77.594600, 120d, NOW_MILLIS)));

        assertTrue(ex.getMessage().contains("not accurate enough"));
    }

    @Test
    void missingLocationIsRejectedWhenNoWfhIsApproved() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.of(office(200)));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.markAttendance(9L, "PRESENT", null));

        assertTrue(ex.getMessage().contains("Location is required"));
    }

    @Test
    void missingOfficeConfigurationIsReportedClearly() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.markAttendance(
                        9L, "PRESENT", location(12.972070, 77.594600, 10d, NOW_MILLIS)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not configured"));
    }

    @Test
    void approvedWfhCoveringTodayAllowsPresentWithoutLocation() {

        when(wfhRepository.findApprovedCovering(9L, TODAY))
                .thenReturn(List.of(wfh("APPROVED", TODAY.minusDays(1), TODAY.plusDays(1))));

        AttendanceResponseDTO result = service.markAttendance(9L, "PRESENT", null);

        assertEquals("PRESENT", result.getStatus());
        assertEquals("WFH", result.getWorkMode());
        assertNull(result.getDistanceMeters());
        verify(officeRepository, never()).findByCompanyCompanyId(anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "CANCELLED", "FUTURE", "EXPIRED"})
    void nonApprovedOrNonCoveringWfhDoesNotBypassOfficeVerification(String status) {

        // The repository only returns APPROVED requests covering the date. Every other
        // state therefore looks like "no approved WFH today" to the service.
        when(wfhRepository.findApprovedCovering(9L, TODAY)).thenReturn(List.of());
        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.of(office(200)));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.markAttendance(9L, "PRESENT", null),
                "WFH state " + status + " must not bypass location verification");

        assertTrue(ex.getMessage().contains("Location is required"));
    }

    @Test
    void absentNeedsNoLocationOrOfficeConfiguration() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.empty());

        AttendanceResponseDTO result = service.markAttendance(9L, "ABSENT", null);

        assertEquals("ABSENT", result.getStatus());
        assertNull(result.getWorkMode());
    }

    @Test
    void duplicateAttendanceIsRejectedBeforeAnyWrite() {

        Attendance existing = new Attendance();
        existing.setEmployee(employee);
        existing.setAttendanceDate(TODAY);
        existing.setStatus("PRESENT");

        when(attendanceRepository.findByEmployeeEmployeeIdAndAttendanceDate(9L, TODAY))
                .thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.markAttendance(9L, "ABSENT", null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void employeeCannotUpdateSomebodyElsesRecord() {

        Employee other = employee(10L, 1L);
        Attendance existing = new Attendance();
        existing.setAttendanceId(5L);
        existing.setEmployee(other);
        existing.setAttendanceDate(TODAY);
        existing.setStatus("ABSENT");

        when(attendanceRepository.findById(5L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateEmployeeAttendance(9L, 5L, "PRESENT", null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void switchingFromAbsentToPresentStillRequiresVerification() {

        when(officeRepository.findByCompanyCompanyId(1L)).thenReturn(Optional.of(office(200)));

        Attendance existing = new Attendance();
        existing.setAttendanceId(5L);
        existing.setEmployee(employee);
        existing.setAttendanceDate(TODAY);
        existing.setStatus("ABSENT");

        when(attendanceRepository.findById(5L)).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateEmployeeAttendance(9L, 5L, "PRESENT", null));

        assertTrue(ex.getMessage().contains("Location is required"));
        assertTrue("ABSENT".equals(existing.getStatus()), "record must not be changed on a failed verification");
    }

    // ================= FIXTURES =================

    private static Employee employee(Long id, Long companyId) {
        Company company = new Company();
        company.setCompanyId(companyId);
        company.setCompanyName("Company " + companyId);

        Admin admin = new Admin();
        admin.setAdminId(1L);
        admin.setAdminName("Admin");
        admin.setEmail("admin@example.com");
        admin.setStatus("ACTIVE");
        admin.setCompany(company);

        Employee employee = new Employee();
        employee.setEmployeeId(id);
        employee.setEmployeeName("Employee " + id);
        employee.setEmail("employee" + id + "@example.com");
        employee.setAccountStatus("APPROVED");
        employee.setAdmin(admin);
        return employee;
    }

    private static OfficeLocation office(int radius) {
        OfficeLocation office = new OfficeLocation();
        office.setOfficeLocationId(1L);
        office.setOfficeName("HQ");
        office.setLatitude(BigDecimal.valueOf(12.971599));
        office.setLongitude(BigDecimal.valueOf(77.594566));
        office.setRadiusMeters(radius);
        office.setTimezone("Asia/Kolkata");
        return office;
    }

    private static AttendanceMarkRequest location(
            Double latitude,
            Double longitude,
            Double accuracy,
            Long capturedAt) {
        return new AttendanceMarkRequest(latitude, longitude, accuracy, capturedAt);
    }

    private static WfhRequest wfh(String status, LocalDate start, LocalDate end) {
        WfhRequest wfh = new WfhRequest();
        wfh.setWfhId(3L);
        wfh.setStartDate(start);
        wfh.setEndDate(end);
        wfh.setStatus(status);
        wfh.setReason("Test");
        return wfh;
    }
}
