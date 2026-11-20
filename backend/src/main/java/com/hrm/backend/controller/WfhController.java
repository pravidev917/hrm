package com.hrm.backend.controller;

import com.hrm.backend.dto.WfhRequestCreateDTO;
import com.hrm.backend.dto.WfhResponseDTO;
import com.hrm.backend.dto.WfhReviewDTO;
import com.hrm.backend.dto.WfhRevokeDTO;
import com.hrm.backend.dto.WfhTodayDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.WfhRequestService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Work-from-home workflow.
 *
 * The employee id / administrator id always comes from the authenticated
 * session. No endpoint accepts a company, employee, approval status or WFH flag
 * from the client.
 */
@RestController
@RequestMapping
public class WfhController {

    private final WfhRequestService wfhService;
    private final EmployeeService employeeService;
    private final AdminService adminService;

    public WfhController(
            WfhRequestService wfhService,
            EmployeeService employeeService,
            AdminService adminService) {

        this.wfhService = wfhService;
        this.employeeService = employeeService;
        this.adminService = adminService;
    }

    // ================= EMPLOYEE =================

    @PostMapping("/api/employee/wfh")
    public ResponseEntity<WfhResponseDTO> apply(
            @Valid @RequestBody WfhRequestCreateDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(wfhService.apply(employeeId, request));
    }

    @GetMapping("/api/employee/wfh")
    public ResponseEntity<List<WfhResponseDTO>> getMine(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(authentication.getName());

        return ResponseEntity.ok(wfhService.getEmployeeRequests(employeeId));
    }

    @GetMapping("/api/employee/wfh/today")
    public ResponseEntity<WfhTodayDTO> getToday(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(authentication.getName());

        return ResponseEntity.ok(wfhService.getToday(employeeId));
    }

    @PatchMapping("/api/employee/wfh/{wfhId}/cancel")
    public ResponseEntity<WfhResponseDTO> cancel(
            @PathVariable Long wfhId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(authentication.getName());

        return ResponseEntity.ok(wfhService.cancel(employeeId, wfhId));
    }

    // ================= ADMIN =================

    @GetMapping("/api/admin/wfh")
    public ResponseEntity<List<WfhResponseDTO>> getAll(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());

        return ResponseEntity.ok(wfhService.getAdminRequests(adminId));
    }

    @PatchMapping("/api/admin/wfh/{wfhId}/review")
    public ResponseEntity<WfhResponseDTO> review(
            @PathVariable Long wfhId,
            @Valid @RequestBody WfhReviewDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());

        return ResponseEntity.ok(
                wfhService.review(adminId, wfhId, request));
    }

    /** Withdraw an approval. Existing attendance records are preserved. */
    @PatchMapping("/api/admin/wfh/{wfhId}/revoke")
    public ResponseEntity<WfhResponseDTO> revoke(
            @PathVariable Long wfhId,
            @Valid @RequestBody(required = false) WfhRevokeDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());

        return ResponseEntity.ok(
                wfhService.revoke(adminId, wfhId, request == null ? null : request.comment()));
    }
}
