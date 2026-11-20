package com.hrm.backend.controller;

import com.hrm.backend.dto.PerformanceTrackerDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.PerformanceTrackerService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator performance tracker: monthly resume leaderboard + monthly
 * revenue report. Month is "YYYY-MM" and defaults to the current month, so past
 * months stay viewable.
 */
@RestController
@RequestMapping("/api/admin/performance-tracker")
public class AdminPerformanceTrackerController {

    private final PerformanceTrackerService trackerService;
    private final AdminService adminService;

    public AdminPerformanceTrackerController(
            PerformanceTrackerService trackerService,
            AdminService adminService) {

        this.trackerService = trackerService;
        this.adminService = adminService;
    }

    @GetMapping("/monthly")
    public ResponseEntity<PerformanceTrackerDTO> monthly(
            @RequestParam(required = false) String month,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());

        return ResponseEntity.ok(trackerService.getMonthlyTracker(adminId, month));
    }
}
