package com.hrm.backend.controller;

import com.hrm.backend.dto.OfficeLocationRequest;
import com.hrm.backend.dto.OfficeLocationResponseDTO;
import com.hrm.backend.dto.OfficeLocationSummaryDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.OfficeLocationService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Office settings.
 *
 * Administrator endpoints are company-scoped by the authenticated session and
 * guarded by SecurityConfig (/api/admin/** requires ROLE_ADMIN). Employees get a
 * minimal summary only: no coordinates.
 */
@RestController
@RequestMapping
public class OfficeLocationController {

    private final OfficeLocationService officeLocationService;
    private final AdminService adminService;
    private final EmployeeService employeeService;

    public OfficeLocationController(
            OfficeLocationService officeLocationService,
            AdminService adminService,
            EmployeeService employeeService) {

        this.officeLocationService = officeLocationService;
        this.adminService = adminService;
        this.employeeService = employeeService;
    }

    @GetMapping("/api/admin/office-location")
    public ResponseEntity<OfficeLocationResponseDTO> getForAdmin(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());

        return ResponseEntity.ok(officeLocationService.getForAdmin(adminId));
    }

    @PutMapping("/api/admin/office-location")
    public ResponseEntity<OfficeLocationResponseDTO> saveForAdmin(
            @Valid @RequestBody OfficeLocationRequest request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());

        return ResponseEntity.ok(
                officeLocationService.saveForAdmin(adminId, request));
    }

    @GetMapping("/api/employee/office-location")
    public ResponseEntity<OfficeLocationSummaryDTO> getForEmployee(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(authentication.getName());

        return ResponseEntity.ok(
                officeLocationService.getForEmployee(employeeId));
    }
}
