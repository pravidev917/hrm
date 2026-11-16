package com.hrm.backend.controller;

import com.hrm.backend.dto.LeaderboardDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.service.LeaderboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Team leaderboard: every employee compared with the others who share their role.
 *
 * Admins see their whole team. Employees see the same board for their own admin's
 * team, with their row flagged, so they can see where they stand.
 */
@RestController
@RequestMapping("/api")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final AdminRepository adminRepository;
    private final EmployeeRepository employeeRepository;

    public LeaderboardController(
            LeaderboardService leaderboardService,
            AdminRepository adminRepository,
            EmployeeRepository employeeRepository) {
        this.leaderboardService = leaderboardService;
        this.adminRepository = adminRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * @param period "YYYY-MM" (defaults to the current month) or "all" for all time.
     */
    // A neutral path on purpose: "/api/admin/**" is restricted to ROLE_ADMIN by
    // SecurityConfig, so employees could not reach a shared board through it.
    @GetMapping("/performance/leaderboard")
    public ResponseEntity<LeaderboardDTO> leaderboard(
            @RequestParam(required = false) String period,
            Authentication authentication) {

        Long adminId = resolveAdminId(authentication.getName());
        LocalDate[] range = resolveRange(period);

        return ResponseEntity.ok(
                leaderboardService.build(adminId, range[0], range[1], authentication.getName()));
    }

    /** "all" -> wide range; "YYYY-MM" -> that month; blank -> current month. */
    private LocalDate[] resolveRange(String period) {
        if (period != null && period.equalsIgnoreCase("all")) {
            return new LocalDate[] { LocalDate.of(1970, 1, 1), LocalDate.of(2999, 12, 31) };
        }
        if (period == null || period.isBlank()) {
            YearMonth now = YearMonth.now();
            return new LocalDate[] { now.atDay(1), now.atEndOfMonth() };
        }
        try {
            YearMonth month = YearMonth.parse(period);
            return new LocalDate[] { month.atDay(1), month.atEndOfMonth() };
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Period must be YYYY-MM or 'all', for example 2026-11");
        }
    }

    private Long resolveAdminId(String email) {
        Admin admin = adminRepository.findByEmail(email).orElse(null);
        if (admin != null) return admin.getAdminId();

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));

        return employee.getAdmin().getAdminId();
    }
}
