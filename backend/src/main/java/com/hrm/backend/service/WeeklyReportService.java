
package com.hrm.backend.service;

import com.hrm.backend.dto.WeeklyReportRequestDTO;
import com.hrm.backend.dto.WeeklyReportResponseDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Client;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.WeeklyReport;
import com.hrm.backend.export.SheetBuilder;
import com.hrm.backend.export.TextFilter;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.ClientRepository;
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

    /** Interview stages offered by the weekly report form (stored uppercased). */
    private static final List<String> INTERVIEW_STATUSES =
            List.of("INTERVIEW_SCHEDULED", "SELECTED", "JOINED", "REJECTED", "ON_HOLD");

    private final WeeklyReportRepository repository;
    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;
    private final ClientRepository clientRepository;
    private final CompanyTimeService companyTime;

    public WeeklyReportService(
            WeeklyReportRepository repository,
            EmployeeRepository employeeRepository,
            AdminRepository adminRepository,
            ClientRepository clientRepository,
            CompanyTimeService companyTime) {

        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.adminRepository = adminRepository;
        this.clientRepository = clientRepository;
        this.companyTime = companyTime;
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
        dto.setClientId(report.getClient() == null ? null : report.getClient().getClientId());
        dto.setClientName(report.getClient() == null ? null : report.getClient().getClientName());
        dto.setWeekNumber(report.getWeekNumber());
        dto.setWeekStartDate(report.getWeekStartDate());
        dto.setWeekEndDate(report.getWeekEndDate());
        dto.setProfilesSubmitted(report.getProfilesSubmitted());
        dto.setInterviewsScheduled(report.getInterviewsScheduled());
        dto.setFinalSelections(report.getFinalSelections());
        dto.setJoined(report.getJoined());
        dto.setRevenueGenerated(report.getRevenueGenerated());
        dto.setCandidateName(report.getCandidateName());
        dto.setPosition(report.getPosition());
        dto.setInterviewStatus(report.getInterviewStatus());
        // The client's own position is shown when the week did not name one.
        if (dto.getPosition() == null && report.getClient() != null) {
            dto.setPosition(report.getClient().getPosition());
        }

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

    private LocalDate[] getWeekPeriod(LocalDate reportDate, Employee employee) {

        if (reportDate == null) {
            throw new RuntimeException("Report date is required");
        }

        // Office calendar, not the UTC container clock: see DailyReportService.
        if (reportDate.isAfter(companyTime.todayForEmployee(employee))) {
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
        report.setCandidateName(trimToNull(request.getCandidateName()));
        report.setPosition(trimToNull(request.getPosition()));
        report.setInterviewStatus(normalizeInterviewStatus(request.getInterviewStatus()));
    }

    /** The five interview stages the weekly table understands; blank clears it. */
    private String normalizeInterviewStatus(String raw) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');

        if (!INTERVIEW_STATUSES.contains(value)) {
            throw new RuntimeException(
                    "Interview status must be one of: " + String.join(", ", INTERVIEW_STATUSES));
        }

        return value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Attaches the optional client to a weekly report.
     *
     * Clients are company data, so every client any administrator of the employee's
     * company created is accepted - exactly like the daily report form and the client
     * dropdown the employee sees. A client from another company is refused. A request
     * without a clientId leaves the current client untouched (reports keep their
     * client, including weeks filed before client selection existed).
     */
    private void applyClient(WeeklyReport report, WeeklyReportRequestDTO request) {

        if (request.getClientId() == null) {
            return;
        }

        Long employeeCompanyId = companyIdOf(report.getEmployee());

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new RuntimeException("Client not found"));

        Long clientCompanyId = client.getAdmin() == null || client.getAdmin().getCompany() == null
                ? null
                : client.getAdmin().getCompany().getCompanyId();

        if (employeeCompanyId == null || !employeeCompanyId.equals(clientCompanyId)) {
            throw new RuntimeException("Access denied: client does not belong to this organisation");
        }

        if (!"ACTIVE".equals(client.getStatus())) {
            throw new RuntimeException("Cannot use an inactive client for a weekly report");
        }

        report.setClient(client);
    }

    private Long companyIdOf(Employee employee) {

        if (employee == null || employee.getAdmin() == null || employee.getAdmin().getCompany() == null) {
            return null;
        }

        return employee.getAdmin().getCompany().getCompanyId();
    }

    // EMPLOYEE: CREATE

    public WeeklyReportResponseDTO createReport(
            Long employeeId,
            WeeklyReportRequestDTO request) {

        Employee employee = getApprovedEmployee(employeeId);

        LocalDate[] period = getWeekPeriod(request.getReportDate(), employee);

        WeeklyReport report = new WeeklyReport();

        report.setEmployee(employee);
        report.setWeekStartDate(period[0]);
        report.setWeekEndDate(period[1]);
        report.setWeekNumber(
                request.getReportDate().get(WeekFields.ISO.weekOfWeekBasedYear())
        );

        applyValues(report, request);
        applyClient(report, request);

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

            LocalDate[] period = getWeekPeriod(request.getReportDate(), report.getEmployee());

            if (!report.getWeekStartDate().equals(period[0]) ||
                !report.getWeekEndDate().equals(period[1])) {

                report.setWeekStartDate(period[0]);
                report.setWeekEndDate(period[1]);
                report.setWeekNumber(
                        request.getReportDate().get(
                                WeekFields.ISO.weekOfWeekBasedYear())
                );
            }
        }

        applyValues(report, request);
        applyClient(report, request);

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

        return repository.findByEmployeeInCompanyOf(adminId)
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

            LocalDate[] period = getWeekPeriod(request.getReportDate(), report.getEmployee());

            if (!report.getWeekStartDate().equals(period[0]) ||
                !report.getWeekEndDate().equals(period[1])) {

                report.setWeekStartDate(period[0]);
                report.setWeekEndDate(period[1]);
                report.setWeekNumber(
                        request.getReportDate().get(
                                WeekFields.ISO.weekOfWeekBasedYear())
                );
            }
        }

        applyValues(report, request);
        applyClient(report, request);

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
            .findByEmployeeInCompanyOfAndWeekStartDateBetween(
                    adminId, startDate, endDate)
            .stream()
            .map(this::toDTO)
            .toList();
}

// ADMIN: EXPORT THE WEEKLY REPORTS TABLE (CSV or Excel)

    @Transactional(readOnly = true)
    public SheetBuilder exportReports(Long adminId, String query) {

        SheetBuilder sheet = SheetBuilder.of("Weekly reports",
                "Employee",
                "Week start",
                "Week end",
                "Week number",
                "Client",
                "Profiles submitted",
                "Interviews scheduled",
                "Final selections",
                "Joined",
                "Revenue generated",
                "Candidate name",
                "Position",
                "Interview status");

        for (WeeklyReportResponseDTO row : getAdminReports(adminId)) {

            if (!TextFilter.contains(query,
                    row.getEmployeeName(),
                    row.getClientName(),
                    row.getCandidateName(),
                    row.getPosition(),
                    row.getInterviewStatus())) {
                continue;
            }

            sheet.row(
                    row.getEmployeeName(),
                    row.getWeekStartDate(),
                    row.getWeekEndDate(),
                    row.getWeekNumber(),
                    row.getClientName(),
                    row.getProfilesSubmitted(),
                    row.getInterviewsScheduled(),
                    row.getFinalSelections(),
                    row.getJoined(),
                    row.getRevenueGenerated(),
                    row.getCandidateName(),
                    row.getPosition(),
                    row.getInterviewStatus());
        }

        return sheet;
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
