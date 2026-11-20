package com.hrm.backend.service;

import com.hrm.backend.dto.LeaveRequestDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveManagementRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class LeaveManagementService {

    private static final List<String> BLOCKING_STATUSES =
            List.of("PENDING", "APPROVED");

    private static final List<String> ALLOWED_LEAVE_TYPES =
            List.of(
                    "CASUAL",
                    "SICK",
                    "ANNUAL",
                    "EMERGENCY",
                    "UNPAID"
            );

    private static final List<String> HALF_DAY_PERIODS =
            List.of(
                    "FIRST_HALF",
                    "SECOND_HALF"
            );

    private final LeaveManagementRepository repository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final AdminService adminService;
    private final CompanyScopeService companyScope;

    public LeaveManagementService(
            LeaveManagementRepository repository,
            EmployeeRepository employeeRepository,
            EmployeeService employeeService,
            AdminService adminService,
            CompanyScopeService companyScope) {

        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.adminService = adminService;
        this.companyScope = companyScope;
    }

    // =========================================================
    // EMPLOYEE - APPLY LEAVE
    // =========================================================

    @Transactional
    public LeaveManagement applyLeave(
            Long employeeId,
            LeaveRequestDTO request) {

        Employee employee = getLockedEmployee(employeeId);

        validateEmployeeApproval(employee);
        validateLeaveRequest(request);

        BigDecimal numberOfDays = calculateLeaveDays(request);

        validateNoOverlap(
                employeeId,
                request.startDate(),
                request.endDate(),
                request.halfDayPeriod(),
                null
        );

        LeaveManagement leave = new LeaveManagement();

        leave.setEmployee(employee);
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setNumberOfDays(numberOfDays);
        leave.setHalfDayPeriod(
                normalizeHalfDayPeriod(request.halfDayPeriod())
        );
        leave.setType(normalizeLeaveType(request.type()));
        leave.setReason(request.reason().trim());
        leave.setStatus("PENDING");
        leave.setAdminRemarks(null);
        leave.setApprovedBy(null);
        leave.setReviewedAt(null);

        return repository.save(leave);
    }

    // =========================================================
    // EMPLOYEE - GET ALL LEAVES
    // =========================================================

    @Transactional(readOnly = true)
    public List<LeaveManagement> getEmployeeLeaves(Long employeeId) {

        Employee employee = getEmployeeById(employeeId);

        validateEmployeeApproval(employee);

        return repository
                .findByEmployeeEmployeeIdOrderByStartDateDesc(employeeId);
    }

    // =========================================================
    // EMPLOYEE - LEAVE BALANCE + APPROVAL HISTORY
    // =========================================================

    // =========================================================
    // EMPLOYEE - GET SINGLE LEAVE
    // =========================================================

    @Transactional(readOnly = true)
    public LeaveManagement getEmployeeLeaveById(
            Long employeeId,
            Long leaveId) {

        Employee employee = getEmployeeById(employeeId);

        validateEmployeeApproval(employee);

        LeaveManagement leave = getLeaveById(leaveId);

        validateEmployeeOwnership(leave, employeeId);

        return leave;
    }

    // =========================================================
    // EMPLOYEE - UPDATE PENDING LEAVE
    // =========================================================

    @Transactional
    public LeaveManagement updateLeave(
            Long employeeId,
            Long leaveId,
            LeaveRequestDTO request) {

        Employee employee = getLockedEmployee(employeeId);

        validateEmployeeApproval(employee);
        validateLeaveRequest(request);

        LeaveManagement leave = getLockedLeaveById(leaveId);

        validateEmployeeOwnership(leave, employeeId);

        if (!"PENDING".equals(leave.getStatus())) {
            throw new IllegalStateException(
                    "Only pending leave requests can be updated"
            );
        }

        BigDecimal numberOfDays = calculateLeaveDays(request);

        validateNoOverlap(
                employeeId,
                request.startDate(),
                request.endDate(),
                request.halfDayPeriod(),
                leaveId
        );

        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setNumberOfDays(numberOfDays);
        leave.setHalfDayPeriod(
                normalizeHalfDayPeriod(request.halfDayPeriod())
        );
        leave.setType(normalizeLeaveType(request.type()));
        leave.setReason(request.reason().trim());

        return repository.save(leave);
    }

    // =========================================================
    // EMPLOYEE - CANCEL LEAVE
    // =========================================================

    @Transactional
    public LeaveManagement cancelLeave(
            Long employeeId,
            Long leaveId) {

        Employee employee = getLockedEmployee(employeeId);

        validateEmployeeApproval(employee);

        LeaveManagement leave = getLockedLeaveById(leaveId);

        validateEmployeeOwnership(leave, employeeId);

        if (!"PENDING".equals(leave.getStatus())) {
            throw new IllegalStateException(
                    "Only pending leave requests can be cancelled"
            );
        }

        leave.setStatus("CANCELLED");

        return repository.save(leave);
    }

    // =========================================================
    // ADMIN - GET ALL LEAVES
    // =========================================================

    @Transactional(readOnly = true)
    public List<LeaveManagement> getAdminLeaves(Long adminId) {

        getActiveAdmin(adminId);

        return repository
                .findByEmployeeInCompanyOfOrderByStartDateDesc(adminId);
    }

    // =========================================================
    // ADMIN - GET SINGLE LEAVE
    // =========================================================

    @Transactional(readOnly = true)
    public LeaveManagement getAdminLeaveById(
            Long adminId,
            Long leaveId) {

        Admin admin = getActiveAdmin(adminId);

        LeaveManagement leave = getLeaveById(leaveId);

        validateAdminOwnership(
                leave,
                admin.getAdminId()
        );

        return leave;
    }

    // =========================================================
    // ADMIN - REVIEW LEAVE
    // =========================================================

    @Transactional
    public LeaveManagement reviewLeave(
            Long adminId,
            Long leaveId,
            String requestedStatus,
            String remarks) {

        /*
         * Load the admin once.
         * This admin entity is also used as approvedBy.
         */
        Admin admin = getActiveAdmin(adminId);

        /*
         * IMPORTANT:
         * Lock the leave row before checking/updating its status.
         *
         * This prevents two admins from simultaneously approving/
         * rejecting the same pending leave request.
         */
        LeaveManagement leave = getLockedLeaveById(leaveId);

        /*
         * Make sure this leave belongs to an employee
         * assigned to the currently logged-in admin.
         */
        validateAdminOwnership(
                leave,
                adminId
        );

        /*
         * Only PENDING requests can be reviewed.
         */
        if (!"PENDING".equals(leave.getStatus())) {
            throw new IllegalStateException(
                    "Only pending leave requests can be reviewed"
            );
        }

        /*
         * Validate requested review status.
         */
        if (requestedStatus == null ||
                requestedStatus.isBlank()) {

            throw new IllegalArgumentException(
                    "Review status is required"
            );
        }

        String status = requestedStatus
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!List.of("APPROVED", "REJECTED").contains(status)) {

            throw new IllegalArgumentException(
                    "Review status must be APPROVED or REJECTED"
            );
        }

        /*
         * Rejection requires remarks.
         */
        if ("REJECTED".equals(status) &&
                (remarks == null || remarks.isBlank())) {

            throw new IllegalArgumentException(
                    "Remarks are required when rejecting leave"
            );
        }

        /*
         * Prevent oversized remarks.
         */
        if (remarks != null &&
                remarks.length() > 2000) {

            throw new IllegalArgumentException(
                    "Remarks cannot exceed 2000 characters"
            );
        }

        /*
         * Apply review.
         */
        leave.setStatus(status);
        leave.setApprovedBy(admin);

        leave.setAdminRemarks(
                remarks == null || remarks.isBlank()
                        ? null
                        : remarks.trim()
        );

        leave.setReviewedAt(LocalDateTime.now());

        return repository.save(leave);
    }

    // =========================================================
    // LOCKED EMPLOYEE
    // =========================================================

    private Employee getLockedEmployee(Long employeeId) {

        return employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Employee not found"
                        )
                );
    }

    // =========================================================
    // LOCKED LEAVE
    // =========================================================

    private LeaveManagement getLockedLeaveById(Long leaveId) {

        return repository.findByIdForUpdate(leaveId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Leave request not found"
                        )
                );
    }

    // =========================================================
    // NORMAL EMPLOYEE LOOKUP
    // =========================================================

    private Employee getEmployeeById(Long employeeId) {

        return employeeService.getEmployeeById(employeeId);
    }

    // =========================================================
    // NORMAL LEAVE LOOKUP
    // =========================================================

    private LeaveManagement getLeaveById(Long leaveId) {

        return repository.findById(leaveId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Leave request not found"
                        )
                );
    }

    // =========================================================
    // ACTIVE ADMIN
    // =========================================================

    private Admin getActiveAdmin(Long adminId) {

        Admin admin = adminService.getAdminRecord(adminId);

        if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())) {

            throw new IllegalStateException(
                    "Admin account is not active"
            );
        }

        return admin;
    }

    // =========================================================
    // EMPLOYEE APPROVAL VALIDATION
    // =========================================================

    private void validateEmployeeApproval(Employee employee) {

        if (!"APPROVED".equalsIgnoreCase(
                employee.getAccountStatus())) {

            throw new IllegalStateException(
                    "Employee account is not approved"
            );
        }

        if (employee.getAdmin() == null) {

            throw new IllegalStateException(
                    "Employee is not assigned to an admin"
            );
        }
    }

    // =========================================================
    // EMPLOYEE OWNERSHIP VALIDATION
    // =========================================================

    private void validateEmployeeOwnership(
            LeaveManagement leave,
            Long employeeId) {

        if (leave.getEmployee() == null ||
                !leave.getEmployee()
                        .getEmployeeId()
                        .equals(employeeId)) {

            throw new IllegalStateException(
                    "Access denied: Leave request does not belong to you"
            );
        }
    }

    // =========================================================
    // ADMIN OWNERSHIP VALIDATION
    // =========================================================

    private void validateAdminOwnership(
            LeaveManagement leave,
            Long adminId) {

        // Leave requests are company data: every administrator of the company can
        // review them, and nobody outside it can.
        if (leave.getEmployee() == null ||
                !companyScope.manages(adminId, leave.getEmployee())) {

            throw new IllegalStateException(
                    "Access denied: Leave request does not belong to your company"
            );
        }
    }

    // =========================================================
    // LEAVE REQUEST VALIDATION
    // =========================================================

    private void validateLeaveRequest(
            LeaveRequestDTO request) {

        if (request == null) {

            throw new IllegalArgumentException(
                    "Leave request is required"
            );
        }

        if (request.startDate() == null) {

            throw new IllegalArgumentException(
                    "Start date is required"
            );
        }

        if (request.endDate() == null) {

            throw new IllegalArgumentException(
                    "End date is required"
            );
        }

        LocalDate today = LocalDate.now();

        if (request.startDate().isBefore(today)) {

            throw new IllegalArgumentException(
                    "Past leave dates are not allowed"
            );
        }

        if (request.endDate().isBefore(
                request.startDate())) {

            throw new IllegalArgumentException(
                    "End date cannot be before start date"
            );
        }

        String leaveType =
                normalizeLeaveType(request.type());

        if (!ALLOWED_LEAVE_TYPES.contains(leaveType)) {

            throw new IllegalArgumentException(
                    "Leave type must be one of: "
                            + String.join(", ", ALLOWED_LEAVE_TYPES)
            );
        }

        if (request.reason() == null ||
                request.reason().isBlank()) {

            throw new IllegalArgumentException(
                    "Leave reason is required"
            );
        }

        if (request.reason().trim().length() > 2000) {

            throw new IllegalArgumentException(
                    "Reason cannot exceed 2000 characters"
            );
        }

        String halfDayPeriod =
                normalizeHalfDayPeriod(
                        request.halfDayPeriod()
                );

        /*
         * If half-day is specified, it must be a valid value.
         */
        if (request.halfDayPeriod() != null &&
                !request.halfDayPeriod().isBlank() &&
                !HALF_DAY_PERIODS.contains(
                        halfDayPeriod)) {

            throw new IllegalArgumentException(
                    "Half-day period must be FIRST_HALF or SECOND_HALF"
            );
        }

        /*
         * Half-day can only be requested for one date.
         */
        if (halfDayPeriod != null &&
                !request.startDate().equals(
                        request.endDate())) {

            throw new IllegalArgumentException(
                    "Half-day leave must be for a single date"
            );
        }

        /*
         * Sunday is not a working day.
         */
        if (request.startDate().getDayOfWeek()
                == DayOfWeek.SUNDAY
                &&
                request.endDate().equals(
                        request.startDate())) {

            throw new IllegalArgumentException(
                    "Leave period must contain at least one working day"
            );
        }
    }

    // =========================================================
    // CALCULATE LEAVE DAYS
    // =========================================================

    private BigDecimal calculateLeaveDays(
            LeaveRequestDTO request) {

        String halfDayPeriod =
                normalizeHalfDayPeriod(
                        request.halfDayPeriod()
                );

        /*
         * Half-day = 0.50 working day.
         */
        if (halfDayPeriod != null) {

            if (request.startDate()
                    .getDayOfWeek()
                    == DayOfWeek.SUNDAY) {

                throw new IllegalArgumentException(
                        "Leave period must contain at least one working day"
                );
            }

            return new BigDecimal("0.50");
        }

        /*
         * Full-day / multi-day leave.
         *
         * Monday through Saturday = working days.
         * Sunday = excluded.
         */
        LocalDate current = request.startDate();

        int workingDays = 0;

        while (!current.isAfter(
                request.endDate())) {

            if (current.getDayOfWeek()
                    != DayOfWeek.SUNDAY) {

                workingDays++;
            }

            current = current.plusDays(1);
        }

        if (workingDays == 0) {

            throw new IllegalArgumentException(
                    "Leave period must contain at least one working day"
            );
        }

        return BigDecimal.valueOf(workingDays);
    }

    // =========================================================
    // OVERLAP VALIDATION
    // =========================================================

    private void validateNoOverlap(
            Long employeeId,
            LocalDate requestedStartDate,
            LocalDate requestedEndDate,
            String requestedHalfDayPeriod,
            Long excludeLeaveId) {

        List<LeaveManagement> existingLeaves =
                repository
                        .findByEmployeeEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                                employeeId,
                                BLOCKING_STATUSES,
                                requestedEndDate,
                                requestedStartDate
                        );

        String requestedHalfDay =
                normalizeHalfDayPeriod(
                        requestedHalfDayPeriod
                );

        for (LeaveManagement existing :
                existingLeaves) {

            /*
             * Ignore the same leave during update.
             */
            if (excludeLeaveId != null &&
                    existing.getLeaveId()
                            .equals(excludeLeaveId)) {

                continue;
            }

            /*
             * Full-day request conflicts with any
             * pending/approved leave that overlaps.
             */
            if (requestedHalfDay == null) {

                throw new IllegalArgumentException(
                        "Leave dates overlap with an existing pending or approved request"
                );
            }

            String existingHalfDay =
                    normalizeHalfDayPeriod(
                            existing.getHalfDayPeriod()
                    );

            /*
             * Existing full-day leave conflicts with
             * any half-day request.
             */
            if (existingHalfDay == null) {

                throw new IllegalArgumentException(
                        "Leave dates overlap with an existing pending or approved request"
                );
            }

            /*
             * Two different half-days on the same date
             * are allowed:
             *
             * FIRST_HALF + SECOND_HALF = allowed
             *
             * FIRST_HALF + FIRST_HALF = blocked
             * SECOND_HALF + SECOND_HALF = blocked
             */
            boolean sameSingleDate =
                    requestedStartDate.equals(
                            requestedEndDate
                    )
                    &&
                    existing.getStartDate().equals(
                            existing.getEndDate()
                    )
                    &&
                    requestedStartDate.equals(
                            existing.getStartDate()
                    );

            if (sameSingleDate &&
                    !requestedHalfDay.equals(
                            existingHalfDay)) {

                continue;
            }

            throw new IllegalArgumentException(
                    "Leave dates overlap with an existing pending or approved request"
            );
        }
    }

    // =========================================================
    // NORMALIZE LEAVE TYPE
    // =========================================================

    private String normalizeLeaveType(
            String type) {

        if (type == null ||
                type.isBlank()) {

            return "";
        }

        return type.trim()
                .toUpperCase(Locale.ROOT);
    }

    // =========================================================
    // NORMALIZE HALF-DAY PERIOD
    // =========================================================

    private String normalizeHalfDayPeriod(
            String halfDayPeriod) {

        if (halfDayPeriod == null ||
                halfDayPeriod.isBlank()) {

            return null;
        }

        return halfDayPeriod
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}