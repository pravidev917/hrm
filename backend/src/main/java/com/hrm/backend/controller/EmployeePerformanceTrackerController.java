package com.hrm.backend.controller;

import com.hrm.backend.dto.EmployeeTrackerDTO;
import com.hrm.backend.service.PerformanceTrackerService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The signed-in employee's own tracker: daily resume target, monthly revenue
 * target and the same-day comparison with colleagues.
 *
 * The account comes from the session, so an employee can never ask for someone
 * else's numbers; the comparison list contains resume counts only.
 */
@RestController
@RequestMapping("/api/employee/performance-tracker")
public class EmployeePerformanceTrackerController {

    private final PerformanceTrackerService trackerService;

    public EmployeePerformanceTrackerController(PerformanceTrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @GetMapping
    public ResponseEntity<EmployeeTrackerDTO> tracker(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {

        return ResponseEntity.ok(
                trackerService.getEmployeeTracker(authentication.getName(), date));
    }
}
