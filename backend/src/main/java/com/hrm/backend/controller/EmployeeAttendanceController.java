package com.hrm.backend.controller;

import com.hrm.backend.dto.EmployeeAttendanceHistoryDTO;
import com.hrm.backend.dto.EmployeeAttendanceTodayDTO;
import com.hrm.backend.service.AttendanceSessionService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Employee attendance: Log In, Log Out, today and history.
 *
 * There is no employee id in any of these requests. The employee is whoever the
 * authenticated cookie says it is, so one employee can never read or write another
 * employee's attendance by changing a parameter.
 *
 * Attendance logout is not application logout: closing a session leaves the
 * authentication cookie (and the login) untouched.
 */
@RestController
@RequestMapping("/api/employee/attendance")
public class EmployeeAttendanceController {

    private final AttendanceSessionService sessionService;

    public EmployeeAttendanceController(AttendanceSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/login")
    public ResponseEntity<EmployeeAttendanceTodayDTO> login(Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.login(authentication.getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<EmployeeAttendanceTodayDTO> logout(Authentication authentication) {
        return ResponseEntity.ok(sessionService.logout(authentication.getName()));
    }

    @GetMapping("/today")
    public ResponseEntity<EmployeeAttendanceTodayDTO> today(Authentication authentication) {
        return ResponseEntity.ok(sessionService.today(authentication.getName()));
    }

    @GetMapping("/history")
    public ResponseEntity<EmployeeAttendanceHistoryDTO> history(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            Authentication authentication) {

        return ResponseEntity.ok(
                sessionService.history(authentication.getName(), from, to));
    }
}
