package com.hrm.backend.controller;

import com.hrm.backend.dto.LeaveRequestDTO;
import com.hrm.backend.dto.LeaveReviewDTO;
import com.hrm.backend.dto.LeaveResponseDTO;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.LeaveManagementService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class LeaveManagementController {

    private final LeaveManagementService leaveService;
    private final EmployeeService employeeService;
    private final AdminService adminService;

    public LeaveManagementController(
            LeaveManagementService leaveService,
            EmployeeService employeeService,
            AdminService adminService) {

        this.leaveService = leaveService;
        this.employeeService = employeeService;
        this.adminService = adminService;
    }

    // Convert entity to response DTO
    private LeaveResponseDTO toResponse(LeaveManagement leave) {

        return new LeaveResponseDTO(
                leave.getLeaveId(),

                leave.getEmployee().getEmployeeId(),
                leave.getEmployee().getEmployeeName(),

                leave.getApprovedBy() != null
                        ? leave.getApprovedBy().getAdminId()
                        : null,

                leave.getApprovedBy() != null
                        ? leave.getApprovedBy().getAdminName()
                        : null,

                leave.getStartDate(),
                leave.getEndDate(),

                leave.getNumberOfDays(),
                leave.getHalfDayPeriod(),

                leave.getType(),
                leave.getReason(),
                leave.getStatus(),

                leave.getAdminRemarks(),
                leave.getReviewedAt()
        );
    }

    // ================= EMPLOYEE =================

    @PostMapping("/api/employee/leaves")
    public ResponseEntity<LeaveResponseDTO> applyLeave(
            @Valid @RequestBody LeaveRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        LeaveManagement leave = leaveService.applyLeave(
                employeeId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(leave));
    }

    @GetMapping("/api/employee/leaves")
    public ResponseEntity<List<LeaveResponseDTO>> getEmployeeLeaves(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        List<LeaveResponseDTO> response = leaveService
                .getEmployeeLeaves(employeeId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Leave position and review history for the signed-in employee.
     * No annual entitlement is reported because none is configured in this system.
     */
    @GetMapping("/api/employee/leaves/{leaveId}")
    public ResponseEntity<LeaveResponseDTO> getEmployeeLeaveById(
            @PathVariable Long leaveId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(toResponse(
                leaveService.getEmployeeLeaveById(
                        employeeId, leaveId)));
    }

    @PutMapping("/api/employee/leaves/{leaveId}")
    public ResponseEntity<LeaveResponseDTO> updateLeave(
            @PathVariable Long leaveId,
            @Valid @RequestBody LeaveRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(toResponse(
                leaveService.updateLeave(
                        employeeId, leaveId, request)));
    }

    @PatchMapping("/api/employee/leaves/{leaveId}/cancel")
    public ResponseEntity<LeaveResponseDTO> cancelLeave(
            @PathVariable Long leaveId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(toResponse(
                leaveService.cancelLeave(employeeId, leaveId)));
    }

    // ================= ADMIN =================

    @GetMapping("/api/admin/leaves")
    public ResponseEntity<List<LeaveResponseDTO>> getAdminLeaves(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        List<LeaveResponseDTO> response = leaveService
                .getAdminLeaves(adminId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/admin/leaves/{leaveId}")
    public ResponseEntity<LeaveResponseDTO> getAdminLeaveById(
            @PathVariable Long leaveId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(toResponse(
                leaveService.getAdminLeaveById(
                        adminId, leaveId)));
    }

    @PatchMapping("/api/admin/leaves/{leaveId}/review")
    public ResponseEntity<LeaveResponseDTO> reviewLeave(
            @PathVariable Long leaveId,
            @Valid @RequestBody LeaveReviewDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        LeaveManagement leave = leaveService.reviewLeave(
                adminId,
                leaveId,
                request.status(),
                request.remarks());

        return ResponseEntity.ok(toResponse(leave));
    }
}