package com.hrm.backend.controller;

import com.hrm.backend.dto.AttendanceResponseDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.AttendanceService;
import com.hrm.backend.service.EmployeeService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final EmployeeService employeeService;
    private final AdminService adminService;

    public AttendanceController(
            AttendanceService attendanceService,
            EmployeeService employeeService,
            AdminService adminService) {

        this.attendanceService = attendanceService;
        this.employeeService = employeeService;
        this.adminService = adminService;
    }

    // ================= EMPLOYEE =================

    @PostMapping("/api/employee/attendance")
    public ResponseEntity<AttendanceResponseDTO> markAttendance(
            @RequestParam String status,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceService.markAttendance(
                        employeeId, status));
    }

    @GetMapping("/api/employee/attendance")
    public ResponseEntity<List<AttendanceResponseDTO>>
    getEmployeeAttendance(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                attendanceService.getEmployeeAttendance(employeeId));
    }

    @GetMapping("/api/employee/attendance/{attendanceId}")
    public ResponseEntity<AttendanceResponseDTO>
    getEmployeeAttendanceById(
            @PathVariable Long attendanceId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                attendanceService.getEmployeeAttendanceById(
                        employeeId, attendanceId));
    }

    @GetMapping("/api/employee/attendance/date-range")
    public ResponseEntity<List<AttendanceResponseDTO>>
    getEmployeeAttendanceByDateRange(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                attendanceService.getEmployeeAttendanceByDateRange(
                        employeeId,
                        startDate,
                        endDate));
    }

    @PutMapping("/api/employee/attendance/{attendanceId}")
    public ResponseEntity<AttendanceResponseDTO>
    updateEmployeeAttendance(
            @PathVariable Long attendanceId,
            @RequestParam String status,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                attendanceService.updateEmployeeAttendance(
                        employeeId,
                        attendanceId,
                        status));
    }

    // ================= ADMIN =================

    @GetMapping("/api/admin/attendance")
    public ResponseEntity<List<AttendanceResponseDTO>>
    getAdminAttendance(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                attendanceService.getAdminAttendance(adminId));
    }

    @GetMapping("/api/admin/attendance/{attendanceId}")
    public ResponseEntity<AttendanceResponseDTO>
    getAdminAttendanceById(
            @PathVariable Long attendanceId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                attendanceService.getAdminAttendanceById(
                        adminId,
                        attendanceId));
    }

    @GetMapping("/api/admin/attendance/date-range")
    public ResponseEntity<List<AttendanceResponseDTO>>
    getAdminAttendanceByDateRange(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                attendanceService.getAdminAttendanceByDateRange(
                        adminId,
                        startDate,
                        endDate));
    }

    @PutMapping("/api/admin/attendance/{attendanceId}")
    public ResponseEntity<AttendanceResponseDTO>
    updateAdminAttendance(
            @PathVariable Long attendanceId,
            @RequestParam String status,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                attendanceService.updateAdminAttendance(
                        adminId,
                        attendanceId,
                        status));
    }

    @DeleteMapping("/api/admin/attendance/{attendanceId}")
    public ResponseEntity<Void> deleteAdminAttendance(
            @PathVariable Long attendanceId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        attendanceService.deleteAdminAttendance(
                adminId,
                attendanceId);

        return ResponseEntity.noContent().build();
    }
}