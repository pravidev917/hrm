
package com.hrm.backend.service;

import com.hrm.backend.dto.WeeklyReportRequestDTO;
import com.hrm.backend.dto.WeeklyReportResponseDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.WeeklyReport;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.WeeklyReportRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;

@Service
@Transactional
public class WeeklyReportService {

    private final WeeklyReportRepository repository;
    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;

    public WeeklyReportService(
            WeeklyReportRepository repository,
            EmployeeRepository employeeRepository,
            AdminRepository adminRepository) {

        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.adminRepository = adminRepository;
    }

    private Employee getApprovedEmployee(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

        if (!"APPROVED".equalsIgnoreCase(employee.getAccountStatus())) {
            throw new RuntimeException("Employee account is not approved");
        }

        return employee;
    }

    private Admin getActiveAdmin(Long adminId) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() ->
                        new RuntimeException("Admin not found"));

        if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())) {
            throw new RuntimeException("Admin account is not active");
        }

        return admin;
    }

    private WeeklyReportResponseDTO toDTO(WeeklyReport report) {

        WeeklyReportResponseDTO dto = new WeeklyReportResponseDTO();

        dto.setWeeklyReportId(report.getWeeklyReportId());
        dto.setEmployeeId(report.getEmployee().getEmployeeId());
        dto.setEmployeeName(report.getEmployee().getEmployeeName());
        dto.setWeekNumber(report.getWeekNumber());
        dto.setWeekStartDate(report.getWeekStartDate());
        dto.setWeekEndDate(report.getWeekEndDate());
        dto.setProfilesSubmitted(report.getProfilesSubmitted());
        dto.setInterviewsScheduled(report.getInterviewsScheduled());
        dto.setFinalSelections(report.getFinalSelections());
        dto.setJoined(report.getJoined());
        dto.setRevenueGenerated(report.getRevenueGenerated());

        return dto;
    }

    private void validateCounts(WeeklyReportRequestDTO request) {

        if (request.getProfilesSubmitted() == null ||
            request.getInterviewsScheduled() == null ||
            request.getFinalSelections() == null ||
            request.getJoined() == null) {

            throw new RuntimeException("Report counts are required");
        }

        if (request.getProfilesSubmitted() < 0 ||
            request.getInterviewsScheduled() < 0 ||
            request.getFinalSelections() < 0 ||
            request.getJoined() < 0) {

            throw new RuntimeException("Report counts cannot be negative");
        }

        if (request.getRevenueGenerated() == null ||
            request.getRevenueGenerated().compareTo(BigDecimal.ZERO) < 0) {

            throw new RuntimeException("Revenue cannot be negative or null");
        }
    }

    private LocalDate[] getWeekPeriod(LocalDate reportDate) {

        if (reportDate == null) {
            throw new RuntimeException("Report date is required");
        }

        if (reportDate.isAfter(LocalDate.now())) {
            throw new RuntimeException("Future report dates are not allowed");
        }

        LocalDate start = reportDate.with(
                DayOfWeek.MONDAY.equals(reportDate.getDayOfWeek())
                        ? java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                        : java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        LocalDate end = start.plusDays(6);

        return new LocalDate[]{start, end};
    }

    private void applyValues(
            WeeklyReport report,
            WeeklyReportRequestDTO request) {

        validateCounts(request);

        report.setProfilesSubmitted(request.getProfilesSubmitted());
        report.setInterviewsScheduled(request.getInterviewsScheduled());
        report.setFinalSelections(request.getFinalSelections());
        report.setJoined(request.getJoined());
        report.setRevenueGenerated(request.getRevenueGenerated());
    }

    // EMPLOYEE: CREATE

    public WeeklyReportResponseDTO createReport(
            Long employeeId,
            WeeklyReportRequestDTO request) {

        Employee employee = getApprovedEmployee(employeeId);

        LocalDate[] period = getWeekPeriod(request.getReportDate());

        if (repository
                .findByEmployeeEmployeeIdAndWeekStartDateAndWeekEndDate(
                        employeeId, period[0], period[1])
                .isPresent()) {

            throw new RuntimeException(
                    "Weekly report already exists for this employee and week");
        }

        WeeklyReport report = new WeeklyReport();

        report.setEmployee(employee);
        report.setWeekStartDate(period[0]);
        report.setWeekEndDate(period[1]);
        report.setWeekNumber(
                request.getReportDate().get(WeekFields.ISO.weekOfWeekBasedYear())
        );

        applyValues(report, request);

        return toDTO(repository.save(report));
    }

    // EMPLOYEE: VIEW ALL OWN REPORTS

    @Transactional(readOnly = true)
    public List<WeeklyReportResponseDTO> getEmployeeReports(Long employeeId) {

        getApprovedEmployee(employeeId);

        return repository.findByEmployeeEmployeeId(employeeId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // EMPLOYEE: VIEW ONE REPORT

    @Transactional(readOnly = true)
    public WeeklyReportResponseDTO getEmployeeReportById(
            Long employeeId, Long reportId) {

        getApprovedEmployee(employeeId);

        WeeklyReport report = repository
                .findByWeeklyReportIdAndEmployeeEmployeeId(reportId, employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Weekly report not found"));

        return toDTO(report);
    }

    // EMPLOYEE: UPDATE OWN REPORT

    public WeeklyReportResponseDTO updateEmployeeReport(
            Long employeeId,
            Long reportId,
            WeeklyReportRequestDTO request) {

        getApprovedEmployee(employeeId);

        WeeklyReport report = repository
                .findByWeeklyReportIdAndEmployeeEmployeeId(reportId, employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Weekly report not found"));

        if (request.getReportDate() != null) {

            LocalDate[] period = getWeekPeriod(request.getReportDate());

            if (!report.getWeekStartDate().equals(period[0]) ||
                !report.getWeekEndDate().equals(period[1])) {

                boolean duplicate = repository
                        .findByEmployeeEmployeeIdAndWeekStartDateAndWeekEndDate(
                                employeeId, period[0], period[1])
                        .filter(existing ->
                                !existing.getWeeklyReportId()
                                        .equals(reportId))
                        .isPresent();

                if (duplicate) {
                    throw new RuntimeException(
                            "Weekly report already exists for this week");
                }

                report.setWeekStartDate(period[0]);
                report.setWeekEndDate(period[1]);
                report.setWeekNumber(
                        request.getReportDate().get(
                                WeekFields.ISO.weekOfWeekBasedYear())
                );
            }
        }

        applyValues(report, request);

        return toDTO(repository.save(report));
    }

    // EMPLOYEE: DELETE OWN REPORT

    public void deleteEmployeeReport(Long employeeId, Long reportId) {

        getApprovedEmployee(employeeId);

        WeeklyReport report = repository
                .findByWeeklyReportIdAndEmployeeEmployeeId(reportId, employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Weekly report not found"));

        repository.delete(report);
    }

    // ADMIN: VIEW ALL REPORTS FOR OWN EMPLOYEES

    @Transactional(readOnly = true)
    public List<WeeklyReportResponseDTO> getAdminReports(Long adminId) {

        getActiveAdmin(adminId);

        return repository.findByEmployeeAdminAdminId(adminId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ADMIN: VIEW ONE REPORT

    @Transactional(readOnly = true)
    public WeeklyReportResponseDTO getAdminReportById(
            Long adminId, Long reportId) {

        getActiveAdmin(adminId);

        WeeklyReport report = repository.findById(reportId)
                .orElseThrow(() ->
                        new RuntimeException("Weekly report not found"));

        if (!report.getEmployee().getAdmin().getAdminId().equals(adminId)) {
            throw new RuntimeException("Access denied to this weekly report");
        }

        return toDTO(report);
    }

    // ADMIN: UPDATE EMPLOYEE REPORT

    public WeeklyReportResponseDTO updateAdminReport(
            Long adminId,
            Long reportId,
            WeeklyReportRequestDTO request) {

        getActiveAdmin(adminId);

        WeeklyReport report = repository.findById(reportId)
                .orElseThrow(() ->
                        new RuntimeException("Weekly report not found"));

        if (!report.getEmployee().getAdmin().getAdminId().equals(adminId)) {
            throw new RuntimeException("Access denied to this weekly report");
        }

        if (request.getReportDate() != null) {

            LocalDate[] period = getWeekPeriod(request.getReportDate());

            if (!report.getWeekStartDate().equals(period[0]) ||
                !report.getWeekEndDate().equals(period[1])) {

                boolean duplicate = repository
                        .findByEmployeeEmployeeIdAndWeekStartDateAndWeekEndDate(
                                report.getEmployee().getEmployeeId(),
                                period[0],
                                period[1])
                        .filter(existing ->
                                !existing.getWeeklyReportId()
                                        .equals(reportId))
                        .isPresent();

                if (duplicate) {
                    throw new RuntimeException(
                            "Weekly report already exists for this week");
                }

                report.setWeekStartDate(period[0]);
                report.setWeekEndDate(period[1]);
                report.setWeekNumber(
                        request.getReportDate().get(
                                WeekFields.ISO.weekOfWeekBasedYear())
                );
            }
        }

        applyValues(report, request);

        return toDTO(repository.save(report));
    }

    // ADMIN: DELETE EMPLOYEE REPORT

    public void deleteAdminReport(Long adminId, Long reportId) {

        getActiveAdmin(adminId);

        WeeklyReport report = repository.findById(reportId)
                .orElseThrow(() ->
                        new RuntimeException("Weekly report not found"));

        if (!report.getEmployee().getAdmin().getAdminId().equals(adminId)) {
            throw new RuntimeException("Access denied to this weekly report");
        }

        repository.delete(report);
    }
    // EMPLOYEE: FILTER OWN REPORTS BY WEEK PERIOD

@Transactional(readOnly = true)
public List<WeeklyReportResponseDTO> getEmployeeReportsByDateRange(
        Long employeeId,
        LocalDate startDate,
        LocalDate endDate) {

    getApprovedEmployee(employeeId);

    validateDateRange(startDate, endDate);

    return repository
            .findByEmployeeEmployeeIdAndWeekStartDateBetween(
                    employeeId, startDate, endDate)
            .stream()
            .map(this::toDTO)
            .toList();
}

// ADMIN: FILTER EMPLOYEE REPORTS BY WEEK PERIOD

@Transactional(readOnly = true)
public List<WeeklyReportResponseDTO> getAdminReportsByDateRange(
        Long adminId,
        LocalDate startDate,
        LocalDate endDate) {

    getActiveAdmin(adminId);

    validateDateRange(startDate, endDate);

    return repository
            .findByEmployeeAdminAdminIdAndWeekStartDateBetween(
                    adminId, startDate, endDate)
            .stream()
            .map(this::toDTO)
            .toList();
}

// VALIDATE DATE RANGE

private void validateDateRange(
        LocalDate startDate,
        LocalDate endDate) {

    if (startDate == null || endDate == null) {
        throw new RuntimeException("Start date and end date are required");
    }

    if (startDate.isAfter(endDate)) {
        throw new RuntimeException(
                "Start date cannot be after end date");
    }
}
}
