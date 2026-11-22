
package com.hrm.backend.controller;

import com.hrm.backend.dto.DailyReportResponseDTO;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.export.ExportFile;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.DailyReportService;
import com.hrm.backend.service.EmployeeService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DailyReportController {

    private final DailyReportService reportService;
    private final AdminService adminService;
    private final EmployeeService employeeService;

    public DailyReportController(
            DailyReportService reportService,
            AdminService adminService,
            EmployeeService employeeService) {

        this.reportService = reportService;
        this.adminService = adminService;
        this.employeeService = employeeService;
    }

    // =========================
    // EMPLOYEE DAILY REPORTS
    // =========================

    @PostMapping("/employee/reports")
    public ResponseEntity<DailyReportResponseDTO> createReport(
            @RequestParam Long clientId,
            @RequestBody DailyReport report,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.createReport(
                        employeeId, clientId, report));
    }

    @GetMapping("/employee/reports")
    public ResponseEntity<List<DailyReportResponseDTO>> getEmployeeReports(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                reportService.getReportsByEmployee(employeeId));
    }

    @GetMapping("/employee/reports/{reportId}")
    public ResponseEntity<DailyReportResponseDTO> getEmployeeReport(
            @PathVariable Long reportId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                reportService.getEmployeeReport(reportId, employeeId));
    }

    @GetMapping("/employee/reports/date-range")
    public ResponseEntity<List<DailyReportResponseDTO>>
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
                reportService.getReportsByDateRange(
                        employeeId, startDate, endDate));
    }

    @PutMapping("/employee/reports/{reportId}")
    public ResponseEntity<DailyReportResponseDTO> updateEmployeeReport(
            @PathVariable Long reportId,
            @RequestParam(required = false) Long clientId,
            @RequestBody DailyReport report,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                reportService.updateReport(
                        reportId, employeeId, clientId, report));
    }

    @DeleteMapping("/employee/reports/{reportId}")
    public ResponseEntity<Void> deleteEmployeeReport(
            @PathVariable Long reportId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        reportService.deleteReport(reportId, employeeId);

        return ResponseEntity.noContent().build();
    }

    // =========================
    // ADMIN DAILY REPORTS
    // =========================

    /**
     * An administrator files a report for one of their employees (the "Add New"
     * action of the daily reports table). The employee is named in the body.
     */
    @PostMapping("/admin/reports")
    public ResponseEntity<DailyReportResponseDTO> createReportForEmployee(
            @RequestParam Long employeeId,
            @RequestParam Long clientId,
            @RequestBody DailyReport report,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.createReportForEmployee(
                        adminId, employeeId, clientId, report));
    }

    @GetMapping("/admin/reports")
    public ResponseEntity<List<DailyReportResponseDTO>> getAdminReports(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                reportService.getReportsByAdmin(adminId));
    }

    @GetMapping("/admin/reports/{reportId}")
    public ResponseEntity<DailyReportResponseDTO> getAdminReport(
            @PathVariable Long reportId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName());

        return ResponseEntity.ok(
                reportService.getAdminReport(reportId, adminId));
    }

    /**
     * CSV or Excel of the admin reports table (`?format=csv|xlsx&q=&clientId=&date=`),
     * with the same filters the page applies.
     */
    @GetMapping("/admin/reports/export")
    public ResponseEntity<byte[]> exportAdminReports(
            Authentication authentication,
            @RequestParam(name = "format", required = false) String format,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "clientId", required = false) Long clientId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());

        return ExportFile
                .of(reportService.exportReports(adminId, query, clientId, reportDate), format, "daily-reports")
                .toResponse();
    }

    @GetMapping("/admin/reports/date-range")
    public ResponseEntity<List<DailyReportResponseDTO>>
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
                reportService.getAdminReportsByDateRange(
                        adminId, startDate, endDate));
    }
}
