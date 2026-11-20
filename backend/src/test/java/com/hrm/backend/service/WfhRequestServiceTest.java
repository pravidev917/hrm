package com.hrm.backend.service;

import com.hrm.backend.dto.WfhRequestCreateDTO;
import com.hrm.backend.dto.WfhResponseDTO;
import com.hrm.backend.dto.WfhReviewDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Company;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.entity.WfhRequest;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.OfficeLocationRepository;
import com.hrm.backend.repository.WfhRequestRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * WFH workflow rules: company boundary, self-approval, dates, overlaps and
 * status transitions. Attendance is intentionally not touched here.
 */
class WfhRequestServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 18);

    private WfhRequestRepository repository;
    private EmployeeRepository employeeRepository;
    private LeaveManagementRepository leaveRepository;
    private AdminService adminService;
    private EmployeeService employeeService;
    private CompanyScopeService companyScope;
    private CompanyTimeService companyTime;
    private WfhRequestService service;

    private Employee employee;
    private Admin admin;

    @BeforeEach
    void setUp() {
        repository = mock(WfhRequestRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        leaveRepository = mock(LeaveManagementRepository.class);
        adminService = mock(AdminService.class);
        employeeService = mock(EmployeeService.class);
        companyScope = mock(CompanyScopeService.class);
        companyTime = mock(CompanyTimeService.class);

        service = new WfhRequestService(
                repository,
                employeeRepository,
                leaveRepository,
                mock(OfficeLocationRepository.class),
                employeeService,
                adminService,
                companyScope,
                companyTime,
                mock(AuditLogService.class));

        employee = employee(9L, 1L, "employee@example.com");
        admin = admin(1L, "admin@example.com");

        when(employeeRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(employee));
        when(employeeService.getEmployeeById(9L)).thenReturn(employee);
        when(adminService.getAdminRecord(1L)).thenReturn(admin);
        when(companyTime.todayForEmployee(any(Employee.class))).thenReturn(TODAY);
        when(companyTime.todayForCompany(1L)).thenReturn(TODAY);
        when(companyScope.manages(eq(1L), any(Employee.class))).thenReturn(true);
        when(repository.save(any(WfhRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void employeeCanSubmitAValidRequest() {

        WfhResponseDTO result = service.apply(
                9L,
                new WfhRequestCreateDTO(TODAY, TODAY.plusDays(2), "Home internet installation"));

        assertEquals("PENDING", result.status());
        assertEquals(9L, result.employeeId());
        assertEquals(TODAY, result.startDate());
    }

    @Test
    void pastDatesAreRejected() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.apply(
                        9L,
                        new WfhRequestCreateDTO(TODAY.minusDays(1), TODAY, "Too late")));

        assertTrue(ex.getMessage().contains("past"));
        verify(repository, never()).save(any());
    }

    @Test
    void endDateBeforeStartDateIsRejected() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.apply(
                        9L,
                        new WfhRequestCreateDTO(TODAY.plusDays(2), TODAY.plusDays(1), "Wrong range")));

        assertTrue(ex.getMessage().contains("before start date"));
    }

    @Test
    void overlappingActiveRequestIsRejected() {

        when(repository.findOverlapping(
                eq(9L), any(), eq(TODAY), eq(TODAY.plusDays(2))))
                .thenReturn(List.of(pending(TODAY, TODAY.plusDays(1))));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.apply(
                        9L,
                        new WfhRequestCreateDTO(TODAY, TODAY.plusDays(2), "Overlap")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("overlap"));
    }

    @Test
    void approvedLeaveBlocksARequest() {

        LeaveManagement leave = new LeaveManagement();
        leave.setLeaveId(2L);
        leave.setEmployee(employee);
        leave.setStartDate(TODAY);
        leave.setEndDate(TODAY.plusDays(1));
        leave.setStatus("APPROVED");

        when(leaveRepository
                .findByEmployeeEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        eq(9L), any(), eq(TODAY.plusDays(1)), eq(TODAY)))
                .thenReturn(List.of(leave));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.apply(
                        9L,
                        new WfhRequestCreateDTO(TODAY, TODAY.plusDays(1), "Overlaps leave")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("leave"));
    }

    @Test
    void administratorOfAnotherCompanyCannotReview() {

        WfhRequest request = pending(TODAY, TODAY.plusDays(1));
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(companyScope.manages(eq(1L), any(Employee.class))).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.review(1L, 3L, new WfhReviewDTO("APPROVED", null)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void selfApprovalIsRejected() {

        Employee self = employee(9L, 1L, "admin@example.com");
        WfhRequest request = pending(TODAY, TODAY.plusDays(1));
        request.setEmployee(self);

        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(companyScope.manages(1L, self)).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.review(1L, 3L, new WfhReviewDTO("APPROVED", null)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("own"));
    }

    @Test
    void expiredPendingRequestCannotBeApproved() {

        WfhRequest request = pending(TODAY.minusDays(3), TODAY.minusDays(1));
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.review(1L, 3L, new WfhReviewDTO("APPROVED", null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already ended"));
    }

    @Test
    void approvalRecordsWhoDecidedAndWhen() {

        WfhRequest request = pending(TODAY, TODAY.plusDays(1));
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));
        when(repository.findOverlapping(anyLong(), any(), any(), any())).thenReturn(List.of());

        WfhResponseDTO result = service.review(
                1L, 3L, new WfhReviewDTO("APPROVED", "Approved for the sprint"));

        assertEquals("APPROVED", result.status());
        assertEquals(1L, result.reviewedByAdminId());
        assertEquals("Admin", result.reviewedByName());
        assertTrue(result.reviewedAt() != null);
        assertEquals("Approved for the sprint", result.adminComment());
        assertTrue(result.coversToday());
    }

    @Test
    void onlyPendingRequestsCanBeCancelledByTheEmployee() {

        WfhRequest request = pending(TODAY, TODAY.plusDays(1));
        request.setStatus("APPROVED");
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.cancel(9L, 3L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void employeeCannotCancelSomebodyElsesRequest() {

        Employee other = employee(10L, 1L, "other@example.com");
        WfhRequest request = pending(TODAY, TODAY.plusDays(1));
        request.setEmployee(other);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.cancel(9L, 3L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void onlyApprovedRequestsCanBeRevoked() {

        WfhRequest request = pending(TODAY, TODAY.plusDays(1));
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.revoke(1L, 3L, "No longer needed"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // ================= FIXTURES =================

    private static Employee employee(Long id, Long companyId, String email) {
        Company company = new Company();
        company.setCompanyId(companyId);
        company.setCompanyName("Company " + companyId);

        Admin owner = admin(1L, "owner@example.com");
        owner.setCompany(company);

        Employee employee = new Employee();
        employee.setEmployeeId(id);
        employee.setEmployeeName("Employee " + id);
        employee.setEmail(email);
        employee.setAccountStatus("APPROVED");
        employee.setAdmin(owner);
        return employee;
    }

    private static Admin admin(Long id, String email) {
        Company company = new Company();
        company.setCompanyId(1L);
        company.setCompanyName("Company 1");

        Admin admin = new Admin();
        admin.setAdminId(id);
        admin.setAdminName("Admin");
        admin.setEmail(email);
        admin.setStatus("ACTIVE");
        admin.setCompany(company);
        return admin;
    }

    private static WfhRequest pending(LocalDate start, LocalDate end) {
        WfhRequest request = new WfhRequest();
        request.setWfhId(3L);
        request.setEmployee(employee(9L, 1L, "employee@example.com"));
        request.setStartDate(start);
        request.setEndDate(end);
        request.setReason("Test");
        request.setStatus("PENDING");
        return request;
    }
}
