package com.hrm.backend.controller;

import com.hrm.backend.dto.CalendarEventsDTO;
import com.hrm.backend.service.CalendarEventsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.EmployeeRepository;

import java.time.YearMonth;

/**
 * Calendar side panel data (birthdays, anniversaries, holidays, achievements).
 *
 * Available to both roles: an admin sees the whole team, an employee sees their
 * own admin's team, which is what the panel shows next to their calendar.
 */
@RestController
@RequestMapping("/api")
public class CalendarEventsController {

    private final CalendarEventsService calendarEventsService;
    private final AdminRepository adminRepository;
    private final EmployeeRepository employeeRepository;

    public CalendarEventsController(
            CalendarEventsService calendarEventsService,
            AdminRepository adminRepository,
            EmployeeRepository employeeRepository) {
        this.calendarEventsService = calendarEventsService;
        this.adminRepository = adminRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * @param month "YYYY-MM"; defaults to the current month.
     */
    @GetMapping("/calendar/events")
    public ResponseEntity<CalendarEventsDTO> getEvents(
            @RequestParam(required = false) String month,
            Authentication authentication) {

        Long adminId = resolveAdminId(authentication.getName());
        YearMonth yearMonth = parseMonth(month);

        return ResponseEntity.ok(calendarEventsService.getEvents(adminId, yearMonth));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Month must be in YYYY-MM format, for example 2026-11");
        }
    }

    /** ADMIN: their own id. EMPLOYEE: the admin they belong to. */
    private Long resolveAdminId(String email) {
        Admin admin = adminRepository.findByEmail(email).orElse(null);
        if (admin != null) return admin.getAdminId();

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Account not found"));

        return employee.getAdmin().getAdminId();
    }
}
