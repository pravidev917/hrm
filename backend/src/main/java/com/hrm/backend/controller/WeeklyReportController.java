
package com.hrm.backend.controller;

import com.hrm.backend.dto.WeeklyReportRequestDTO;
import com.hrm.backend.dto.WeeklyReportResponseDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.WeeklyReportService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;
    private final EmployeeService employeeService;
    private final AdminService adminService;

    public WeeklyReportController(
            WeeklyReportService weeklyReportService,
            EmployeeService employeeService,
            AdminService adminService) {

        this.weeklyReportService = weeklyReportService;
        this.employeeService = employeeService;
        this.adminService = adminService;
    }

    // ================= EMPLOYEE =================

    @PostMapping("/api/employee/weekly-reports")
    public ResponseEntity<WeeklyReportResponseDTO> createEmployeeReport(
            @RequestBody WeeklyReportRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(weeklyReportService.createReport(
        employeeId, request));
    }

    @GetMapping("/api/employee/weekly-reports")
    public ResponseEntity<List<WeeklyReportResponseDTO>> getEmployeeReports(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                weeklyReportService.getEmployeeReports(employeeId));
    }

    @GetMapping("/api/employee/weekly-reports/{reportId}")
    public ResponseEntity<WeeklyReportResponseDTO> getEmployeeReport(
            @PathVariable Long reportId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                weeklyReportService.getEmployeeReportById(
        employeeId, reportId));
    }

    @GetMapping("/api/employee/weekly-reports/date-range")
    public ResponseEntity<List<WeeklyReportResponseDTO>>
    getEmployeeReportsByDateRange(
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
                weeklyReportService.getEmployeeReportsByDateRange(
                        employeeId, startDate, endDate));
    }

    @PutMapping("/api/employee/weekly-reports/{reportId}")
    public ResponseEntity<WeeklyReportResponseDTO> updateEmployeeReport(
            @PathVariable Long reportId,
            @RequestBody WeeklyReportRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                weeklyReportService.updateEmployeeReport(
                        employeeId, reportId, request));
    }

    @DeleteMapping("/api/employee/weekly-reports/{reportId}")
    public ResponseEntity<Void> deleteEmployeeReport(
            @PathVariable Long reportId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        weeklyReportService.deleteEmployeeReport(employeeId, reportId);

        return ResponseEntity.noContent().build();
    }

    // ================= ADMIN =================

    @GetMapping("/api/admin/weekly-reports")
    public ResponseEntity<List<WeeklyReportResponseDTO>> getAdminReports(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                weeklyReportService.getAdminReports(adminId));
    }

    @GetMapping("/api/admin/weekly-reports/{reportId}")
    public ResponseEntity<WeeklyReportResponseDTO> getAdminReport(
            @PathVariable Long reportId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                weeklyReportService.getAdminReportById(adminId, reportId));
    }

    @GetMapping("/api/admin/weekly-reports/date-range")
    public ResponseEntity<List<WeeklyReportResponseDTO>>
    getAdminReportsByDateRange(
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
                weeklyReportService.getAdminReportsByDateRange(
                        adminId, startDate, endDate));
    }

    @PutMapping("/api/admin/weekly-reports/{reportId}")
    public ResponseEntity<WeeklyReportResponseDTO> updateAdminReport(
            @PathVariable Long reportId,
            @RequestBody WeeklyReportRequestDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                weeklyReportService.updateAdminReport(
                        adminId, reportId, request));
    }

    @DeleteMapping("/api/admin/weekly-reports/{reportId}")
    public ResponseEntity<Void> deleteAdminReport(
            @PathVariable Long reportId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        weeklyReportService.deleteAdminReport(adminId, reportId);

        return ResponseEntity.noContent().build();
    }
}
