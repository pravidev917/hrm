package com.hrm.backend.controller;

import com.hrm.backend.dto.AdminAttendanceRowDTO;
import com.hrm.backend.dto.AdminAttendanceSummaryDTO;
import com.hrm.backend.dto.EmployeeAttendanceHistoryDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.AttendanceMonitorService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Administrator attendance monitoring.
 *
 * Every mapping lives under /api/admin/**, which the security configuration protects
 * with ROLE_ADMIN; an employee cookie gets 403 before this class is reached.
 *
 * Status semantics (derived from the session rows, never stored by hand):
 * LOGGED_IN, LOGGED_OUT, NOT_LOGGED_IN, INCOMPLETE_SESSION, plus ABSENT, ON_LEAVE,
 * HOLIDAY and WEEKLY_OFF for days judged against the company calendar.
 */
@RestController
@RequestMapping("/api/admin/attendance")
public class AdminAttendanceController {

    private final AttendanceMonitorService monitorService;
    private final AdminService adminService;

    public AdminAttendanceController(
            AttendanceMonitorService monitorService,
            AdminService adminService) {

        this.monitorService = monitorService;
        this.adminService = adminService;
    }

    /** Attendance monitor table: one row per employee per day of the range. */
    @GetMapping
    public ResponseEntity<List<AdminAttendanceRowDTO>> rows(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());
        return ResponseEntity.ok(
                monitorService.rows(adminId, from, to, employeeId, designation, status, q));
    }

    /** Employees with an open session right now (including sessions left open earlier). */
    @GetMapping("/active")
    public ResponseEntity<List<AdminAttendanceRowDTO>> active(Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());
        return ResponseEntity.ok(monitorService.active(adminId));
    }

    /** Full session history of one employee of the company. */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<EmployeeAttendanceHistoryDTO> employeeHistory(
            @PathVariable Long employeeId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());
        return ResponseEntity.ok(
                monitorService.employeeHistory(adminId, employeeId, from, to));
    }

    /** Daily / weekly / monthly working-time totals for the range. */
    @GetMapping("/summary")
    public ResponseEntity<AdminAttendanceSummaryDTO> summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());
        return ResponseEntity.ok(monitorService.summary(adminId, from, to));
    }

    /** CSV of the rows currently on screen, with the same filters applied. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());

        String csv = monitorService.csv(adminId, from, to, employeeId, designation, status, q);

        String fileName = "attendance-" + (from == null ? "range" : from)
                + "-to-" + (to == null ? "today" : to) + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(csv.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }
}
