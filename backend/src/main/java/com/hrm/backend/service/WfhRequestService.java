package com.hrm.backend.service;

import com.hrm.backend.dto.WfhRequestCreateDTO;
import com.hrm.backend.dto.WfhResponseDTO;
import com.hrm.backend.dto.WfhReviewDTO;
import com.hrm.backend.dto.WfhTodayDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.entity.OfficeLocation;
import com.hrm.backend.entity.WfhRequest;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.OfficeLocationRepository;
import com.hrm.backend.repository.WfhRequestRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Work-from-home requests and decisions.
 *
 * Rules enforced here (never trusted from the client):
 *  - dates are validated in the company's timezone and past dates are refused
 *  - active requests of one employee may not overlap
 *  - an overlapping leave request blocks a WFH request (same rule as leave)
 *  - only administrators of the employee's company may review
 *  - an administrator cannot approve their own employee record
 *  - only PENDING requests can be reviewed or cancelled by the employee
 *  - an APPROVED request can be revoked by an administrator; history is audited
 *
 * Attendance is never written here: approval only unlocks WFH attendance when
 * the employee explicitly marks present.
 */
@Service
public class WfhRequestService {

    private static final List<String> ACTIVE_WFH_STATUSES = List.of("PENDING", "APPROVED");
    private static final List<String> BLOCKING_LEAVE_STATUSES = List.of("PENDING", "APPROVED");

    private final WfhRequestRepository repository;
    private final EmployeeRepository employeeRepository;
    private final LeaveManagementRepository leaveRepository;
    private final OfficeLocationRepository officeRepository;
    private final EmployeeService employeeService;
    private final AdminService adminService;
    private final CompanyScopeService companyScope;
    private final CompanyTimeService companyTime;
    private final AuditLogService auditLog;

    public WfhRequestService(
            WfhRequestRepository repository,
            EmployeeRepository employeeRepository,
            LeaveManagementRepository leaveRepository,
            OfficeLocationRepository officeRepository,
            EmployeeService employeeService,
            AdminService adminService,
            CompanyScopeService companyScope,
            CompanyTimeService companyTime,
            AuditLogService auditLog) {

        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.leaveRepository = leaveRepository;
        this.officeRepository = officeRepository;
        this.employeeService = employeeService;
        this.adminService = adminService;
        this.companyScope = companyScope;
        this.companyTime = companyTime;
        this.auditLog = auditLog;
    }

    // ================= EMPLOYEE =================

    @Transactional
    public WfhResponseDTO apply(Long employeeId, WfhRequestCreateDTO request) {

        if (request == null) {
            throw new IllegalArgumentException("WFH request is required");
        }

        Employee employee = lockedApprovedEmployee(employeeId);
        LocalDate today = companyTime.todayForEmployee(employee);

        validateDates(request.startDate(), request.endDate(), today);
        String reason = validateReason(request.reason());

        assertNoOverlap(employeeId, request.startDate(), request.endDate(), null);
        assertNoApprovedLeave(employeeId, request.startDate(), request.endDate());

        WfhRequest wfh = new WfhRequest();
        wfh.setEmployee(employee);
        wfh.setStartDate(request.startDate());
        wfh.setEndDate(request.endDate());
        wfh.setReason(reason);
        wfh.setStatus("PENDING");

        WfhRequest saved = repository.save(wfh);

        auditLog.employee(
                employee,
                "WFH_SUBMITTED",
                "WFH_REQUEST",
                saved.getWfhId(),
                "Requested WFH from " + saved.getStartDate() + " to " + saved.getEndDate());

        return toResponse(saved, today);
    }

    @Transactional(readOnly = true)
    public List<WfhResponseDTO> getEmployeeRequests(Long employeeId) {

        Employee employee = employeeService.getEmployeeById(employeeId);
        requireApproved(employee);

        LocalDate today = companyTime.todayForEmployee(employee);

        return repository.findByEmployeeEmployeeIdOrderByStartDateDesc(employeeId)
                .stream()
                .map(wfh -> toResponse(wfh, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public WfhTodayDTO getToday(Long employeeId) {

        Employee employee = employeeService.getEmployeeById(employeeId);
        requireApproved(employee);

        LocalDate today = companyTime.todayForEmployee(employee);
        WfhRequest covering = approvedCovering(employeeId, today);

        long pending = repository.countByEmployeeEmployeeIdAndStatus(employeeId, "PENDING");

        OfficeLocation office = officeRepository
                .findByCompanyCompanyId(CompanyTimeService.companyIdOf(employee))
                .orElse(null);

        return new WfhTodayDTO(
                today,
                covering != null,
                covering != null ? covering.getWfhId() : null,
                covering != null ? covering.getStartDate() : null,
                covering != null ? covering.getEndDate() : null,
                pending,
                office != null,
                office != null ? office.getOfficeName() : null,
                office != null ? office.getRadiusMeters() : null);
    }

    @Transactional
    public WfhResponseDTO cancel(Long employeeId, Long wfhId) {

        Employee employee = employeeService.getEmployeeById(employeeId);
        requireApproved(employee);

        WfhRequest wfh = lockedRequest(wfhId);

        requireOwnership(wfh, employeeId);

        if (!"PENDING".equals(wfh.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only pending WFH requests can be cancelled");
        }

        wfh.setStatus("CANCELLED");
        WfhRequest saved = repository.save(wfh);

        auditLog.employee(
                employee,
                "WFH_CANCELLED",
                "WFH_REQUEST",
                saved.getWfhId(),
                "Cancelled pending WFH request");

        return toResponse(saved, companyTime.todayForEmployee(employee));
    }

    // ================= ADMIN =================

    @Transactional(readOnly = true)
    public List<WfhResponseDTO> getAdminRequests(Long adminId) {

        Admin admin = activeAdmin(adminId);
        LocalDate today = companyTime.todayForCompany(admin.getCompany().getCompanyId());

        return repository.findByEmployeeInCompanyOf(adminId)
                .stream()
                .map(wfh -> toResponse(wfh, today))
                .toList();
    }

    @Transactional
    public WfhResponseDTO review(Long adminId, Long wfhId, WfhReviewDTO request) {

        Admin admin = activeAdmin(adminId);
        WfhRequest wfh = lockedRequest(wfhId);
        requireCompanyOwnership(wfh, adminId, "Access denied: WFH request belongs to another company");

        if (!"PENDING".equals(wfh.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only pending WFH requests can be reviewed");
        }

        String status = normalizeReviewStatus(request == null ? null : request.status());
        String comment = normalizeComment(request == null ? null : request.comment());
        Employee employee = wfh.getEmployee();

        if (admin.getEmail() != null
                && employee.getEmail() != null
                && admin.getEmail().equalsIgnoreCase(employee.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You cannot review your own work-from-home request");
        }

        LocalDate today = companyTime.todayForEmployee(employee);

        if ("APPROVED".equals(status)) {
            if (wfh.getEndDate().isBefore(today)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This WFH request has already ended and can no longer be approved");
            }

            /*
             * Lock the employee row so a concurrent approval/submission cannot slip a
             * second active request into the same dates.
             */
            lockedApprovedEmployee(employee.getEmployeeId());
            assertNoOverlap(employee.getEmployeeId(), wfh.getStartDate(), wfh.getEndDate(), wfh.getWfhId());
            assertNoApprovedLeave(employee.getEmployeeId(), wfh.getStartDate(), wfh.getEndDate());
        }

        wfh.setStatus(status);
        wfh.setReviewedBy(admin);
        wfh.setReviewedAt(LocalDateTime.now());
        wfh.setAdminComment(comment);

        WfhRequest saved = repository.save(wfh);

        auditLog.admin(
                admin,
                "APPROVED".equals(status) ? "WFH_APPROVED" : "WFH_REJECTED",
                "WFH_REQUEST",
                saved.getWfhId(),
                "Decision " + status + " for employee " + employee.getEmployeeName()
                        + " (" + employee.getEmployeeId() + "), "
                        + saved.getStartDate() + " to " + saved.getEndDate()
                        + (comment == null ? "" : ", comment: " + comment));

        return toResponse(saved, today);
    }

    /**
     * Revoke an approved request. Attendance already recorded against it is left
     * untouched: historical attendance must not be rewritten when approval is
     * withdrawn. The change itself is audited.
     */
    @Transactional
    public WfhResponseDTO revoke(Long adminId, Long wfhId, String comment) {

        Admin admin = activeAdmin(adminId);
        WfhRequest wfh = lockedRequest(wfhId);
        requireCompanyOwnership(wfh, adminId, "Access denied: WFH request belongs to another company");

        if (!"APPROVED".equals(wfh.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only approved WFH requests can be revoked");
        }

        String normalized = normalizeComment(comment);

        wfh.setStatus("CANCELLED");
        wfh.setReviewedBy(admin);
        wfh.setReviewedAt(LocalDateTime.now());
        wfh.setAdminComment(normalized);

        WfhRequest saved = repository.save(wfh);

        auditLog.admin(
                admin,
                "WFH_REVOKED",
                "WFH_REQUEST",
                saved.getWfhId(),
                "Revoked approved WFH for employee " + wfh.getEmployee().getEmployeeName()
                        + " (" + wfh.getEmployee().getEmployeeId() + "), "
                        + saved.getStartDate() + " to " + saved.getEndDate()
                        + ". Existing attendance records were preserved."
                        + (normalized == null ? "" : " Comment: " + normalized));

        return toResponse(saved, companyTime.todayForEmployee(wfh.getEmployee()));
    }

    // ================= ATTENDANCE INTEGRATION =================

    /**
     * The approved WFH request covering a company-local date, or null.
     * Used by attendance verification; only APPROVED counts.
     */
    @Transactional(readOnly = true)
    public WfhRequest approvedCovering(Long employeeId, LocalDate date) {
        return repository.findApprovedCovering(employeeId, date)
                .stream()
                .findFirst()
                .orElse(null);
    }

    // ================= VALIDATION =================

    private void validateDates(LocalDate startDate, LocalDate endDate, LocalDate today) {

        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("End date is required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if (startDate.isBefore(today)) {
            throw new IllegalArgumentException(
                    "Work-from-home cannot be requested for a past date");
        }
    }

    private String validateReason(String reason) {

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason is required");
        }

        String trimmed = reason.trim();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("Reason cannot exceed 2000 characters");
        }
        return trimmed;
    }

    private void assertNoOverlap(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            Long excludeWfhId) {

        List<WfhRequest> overlapping = repository.findOverlapping(
                employeeId, ACTIVE_WFH_STATUSES, startDate, endDate);

        boolean conflict = overlapping.stream()
                .anyMatch(existing -> excludeWfhId == null
                        || !excludeWfhId.equals(existing.getWfhId()));

        if (conflict) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "These dates overlap with an existing pending or approved WFH request");
        }
    }

    private void assertNoApprovedLeave(Long employeeId, LocalDate startDate, LocalDate endDate) {

        List<LeaveManagement> leaves = leaveRepository
                .findByEmployeeEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employeeId, BLOCKING_LEAVE_STATUSES, endDate, startDate);

        if (!leaves.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "These dates overlap with an existing leave request");
        }
    }

    private String normalizeReviewStatus(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Decision is required");
        }

        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "REJECTED").contains(status)) {
            throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
        }
        return status;
    }

    private String normalizeComment(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("Comment cannot exceed 2000 characters");
        }
        return trimmed;
    }

    // ================= LOOKUPS / OWNERSHIP =================

    private Employee lockedApprovedEmployee(Long employeeId) {

        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found"));

        requireApproved(employee);
        return employee;
    }

    private void requireApproved(Employee employee) {

        if (!"APPROVED".equalsIgnoreCase(employee.getAccountStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Employee account is not approved");
        }
    }

    private WfhRequest lockedRequest(Long wfhId) {

        return repository.findByIdForUpdate(wfhId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "WFH request not found"));
    }

    private void requireOwnership(WfhRequest wfh, Long employeeId) {

        if (wfh.getEmployee() == null
                || !wfh.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Access denied: WFH request does not belong to you");
        }
    }

    private void requireCompanyOwnership(WfhRequest wfh, Long adminId, String message) {

        if (wfh.getEmployee() == null || !companyScope.manages(adminId, wfh.getEmployee())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private Admin activeAdmin(Long adminId) {

        Admin admin = adminService.getAdminRecord(adminId);

        if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Admin account is not active");
        }

        return admin;
    }

    // ================= DTO =================

    private WfhResponseDTO toResponse(WfhRequest wfh, LocalDate today) {

        Employee employee = wfh.getEmployee();
        boolean coversToday = "APPROVED".equals(wfh.getStatus())
                && !wfh.getStartDate().isAfter(today)
                && !wfh.getEndDate().isBefore(today);

        return new WfhResponseDTO(
                wfh.getWfhId(),
                employee.getEmployeeId(),
                employee.getEmployeeName(),
                wfh.getStartDate(),
                wfh.getEndDate(),
                wfh.getReason(),
                wfh.getStatus(),
                wfh.getReviewedBy() != null ? wfh.getReviewedBy().getAdminId() : null,
                wfh.getReviewedBy() != null ? wfh.getReviewedBy().getAdminName() : null,
                wfh.getAdminComment(),
                wfh.getReviewedAt(),
                wfh.getCreatedAt(),
                coversToday);
    }
}
