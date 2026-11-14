package com.hrm.backend.controller;

import com.hrm.backend.dto.DashboardDTO;
import com.hrm.backend.dto.EmployeeProfileDTO;
import com.hrm.backend.service.EmployeeDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employee")
public class EmployeeController {

    private final EmployeeDashboardService dashboardService;

    public EmployeeController(EmployeeDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Employee profile is intentionally read-only. */
    @GetMapping("/profile")
    public ResponseEntity<EmployeeProfileDTO> getOwnProfile(
            Authentication authentication) {
        return ResponseEntity.ok(
                dashboardService.getOwnProfile(authentication.getName()));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDTO> getOwnDashboard(
            Authentication authentication) {
        return ResponseEntity.ok(
                dashboardService.getOwnDashboard(authentication.getName()));
    }
}
