package com.hrm.backend.controller;

import com.hrm.backend.dto.ReportingOverviewDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.ReportingService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReportingController {

    private final ReportingService reportingService;
    private final AdminService adminService;
    private final EmployeeService employeeService;

    public ReportingController(
            ReportingService reportingService,
            AdminService adminService,
            EmployeeService employeeService) {
        this.reportingService = reportingService;
        this.adminService = adminService;
        this.employeeService = employeeService;
    }

    @GetMapping("/admin/reporting")
    public ResponseEntity<ReportingOverviewDTO> adminOverview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String platform,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());
        return ResponseEntity.ok(
                reportingService.forAdmin(adminId, from, to, employeeId, clientId, position, platform));
    }

    @GetMapping("/employee/reporting")
    public ResponseEntity<ReportingOverviewDTO> employeeOverview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String platform,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(authentication.getName());
        return ResponseEntity.ok(
                reportingService.forEmployee(employeeId, from, to, clientId, position, platform));
    }
}
