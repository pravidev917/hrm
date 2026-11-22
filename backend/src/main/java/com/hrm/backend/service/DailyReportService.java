
package com.hrm.backend.service;

import com.hrm.backend.dto.DailyReportResponseDTO;
import com.hrm.backend.dto.PlatformCountDTO;
import com.hrm.backend.entity.Client;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.entity.DailyReportPlatformCount;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.export.SheetBuilder;
import com.hrm.backend.export.TextFilter;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.DailyReportPlatformCountRepository;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DailyReportService {

    private final DailyReportRepository reportRepository;
    private final DailyReportPlatformCountRepository platformCountRepository;
    private final EmployeeRepository employeeRepository;
    private final ClientRepository clientRepository;
    private final CompanyScopeService companyScope;
    private final CompanyTimeService companyTime;

    public DailyReportService(
            DailyReportRepository reportRepository,
            DailyReportPlatformCountRepository platformCountRepository,
            EmployeeRepository employeeRepository,
            ClientRepository clientRepository,
            CompanyScopeService companyScope,
            CompanyTimeService companyTime) {

        this.reportRepository = reportRepository;
        this.platformCountRepository = platformCountRepository;
        this.employeeRepository = employeeRepository;
        this.clientRepository = clientRepository;
        this.companyScope = companyScope;
        this.companyTime = companyTime;
    }

    /** Compares the companies of an employee and a client, not their creators. */
    private boolean sameCompany(Employee employee, Client client) {
        if (employee.getAdmin() == null || employee.getAdmin().getCompany() == null
                || client.getAdmin() == null || client.getAdmin().getCompany() == null) {
            return false;
        }
        Long employeeCompany = employee.getAdmin().getCompany().getCompanyId();
        Long clientCompany = client.getAdmin().getCompany().getCompanyId();
        return employeeCompany != null && employeeCompany.equals(clientCompany);
    }

    // Validate report counts
    private void validateCounts(
            Integer profilesSubmitted,
            Integer candidatesSourced,
            Integer candidatesContacted,
            Integer connectedCount) {

        if (profilesSubmitted < 0 ||
                candidatesSourced < 0 ||
                candidatesContacted < 0 ||
                connectedCount < 0) {

            throw new RuntimeException(
                    "Report counts cannot be negative");
        }

        if (connectedCount > candidatesContacted) {
            throw new RuntimeException(
                    "Connected count cannot exceed candidates contacted");
        }

        // The pipeline rule: every later stage is drawn from the profiles that were
        // sourced, so no single stage may be larger than candidatesSourced.
        // 20 sourced / 15 submitted / 8 connected / 4 selected is fine;
        // 10 sourced / 15 submitted is not.
        if (profilesSubmitted > candidatesSourced) {
            throw new RuntimeException(
                    "Profiles submitted (" + profilesSubmitted
                            + ") cannot be more than candidates sourced (" + candidatesSourced
                            + ")");
        }

        if (candidatesContacted > candidatesSourced) {
            throw new RuntimeException(
                    "Candidates contacted (" + candidatesContacted
                            + ") cannot be more than candidates sourced (" + candidatesSourced
                            + ")");
        }

        if (connectedCount > candidatesSourced) {
            throw new RuntimeException(
                    "Connected count (" + connectedCount
                            + ") cannot be more than candidates sourced (" + candidatesSourced
                            + ")");
        }
    }

    // Validate report date against the office calendar. The container runs in UTC, so
    // LocalDate.now() is a day behind the office during the 00:00-05:30 IST window and
    // rejects a report filed for "today".
    private void validateReportDate(LocalDate reportDate, Employee employee) {

        if (reportDate == null) {
            throw new RuntimeException("Report date is required");
        }

        if (reportDate.isAfter(companyTime.todayForEmployee(employee))) {
            throw new RuntimeException(
                    "Future report dates are not allowed");
        }
    }

    // Convert entity into response DTO
    private DailyReportResponseDTO convertToDTO(DailyReport report) {

        return new DailyReportResponseDTO(
                report.getDailyReportId(),
                report.getEmployee().getEmployeeId(),
                report.getEmployee().getEmployeeName(),
                report.getEmployee().getEmployeeCode(),
                report.getEmployee().getRole(),
                report.getClient().getClientId(),
                report.getClient().getClientName(),
                report.getProfilesSubmitted(),
                report.getCandidatesSourced(),
                report.getCandidatesContacted(),
                report.getConnectedCount(),
                report.getReportDate(),
                report.getRemarks(),
                report.getSourcingPlatform(),
                report.getPlatformCounts() == null
                        ? List.of()
                        : report.getPlatformCounts().stream()
                                .map(row -> new PlatformCountDTO(row.getPlatform(), row.getProfileCount()))
                                .toList()
        );
    }

    /**
     * Validates and replaces the report's per-platform rows.
     *
     * Platform counts must reconcile with candidatesSourced; the old single
     * platform field is kept in sync with the largest platform for display.
     */
    private void applyPlatforms(DailyReport report, DailyReport request, int sourced) {

        List<DailyReportPlatformCount> incoming = request.getPlatformCounts();
        String single = trimToNull(request.getSourcingPlatform());

        Map<String, Integer> merged = new LinkedHashMap<>();
        if (incoming != null) {
            for (DailyReportPlatformCount row : incoming) {
                if (row == null) continue;
                String name = trimToNull(row.getPlatform());
                int count = row.getProfileCount() == null ? 0 : row.getProfileCount();
                if (count < 0) {
                    throw new RuntimeException("Platform profile counts cannot be negative");
                }
                if (count == 0) continue;
                if (name == null) {
                    throw new RuntimeException("Choose a sourcing platform for every profile count");
                }
                // Fold retired spellings (Indeed, Referral, Company Website, Other)
                // into the five canonical platforms instead of storing a name no
                // chart or table would show.
                String canonicalName = SourcingPlatforms.canonical(name).orElse(null);
                if (canonicalName == null) {
                    throw new RuntimeException(
                            "Unknown sourcing platform \"" + name + "\". Use: "
                                    + SourcingPlatforms.allowedList());
                }
                merged.merge(canonicalName, count, Integer::sum);
            }
        }

        // Backwards-compatible single-platform payload.
        if (merged.isEmpty() && single != null && sourced > 0) {
            merged.put(SourcingPlatforms.canonicalOrOthers(single), sourced);
        }

        int platformTotal = merged.values().stream().mapToInt(Integer::intValue).sum();
        if (!merged.isEmpty() && platformTotal != sourced) {
            throw new RuntimeException(
                    "Platform counts add up to " + platformTotal
                            + " but candidates sourced is " + sourced
                            + ". They must match so the same profiles are not counted twice.");
        }

        // A saved report already has rows, and (daily_report_id, platform) is unique, so
        // the old ones must go in the database before the new ones arrive. Clearing the
        // collection alone lets Hibernate insert first and trip that constraint.
        if (report.getDailyReportId() != null) {
            platformCountRepository.deleteByReportId(report.getDailyReportId());
        }

        report.getPlatformCounts().clear();
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            DailyReportPlatformCount row = new DailyReportPlatformCount();
            row.setDailyReport(report);
            row.setPlatform(entry.getKey());
            row.setProfileCount(entry.getValue());
            report.getPlatformCounts().add(row);
        }

        report.setSourcingPlatform(merged.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    /**
     * An administrator files a daily report on behalf of one of their employees.
     *
     * Same rules as an employee filing it: the employee must belong to the
     * administrator's company and be active, counts must satisfy the pipeline rule and
     * a second report for the same employee and day is refused.
     */
    @Transactional
    public DailyReportResponseDTO createReportForEmployee(
            Long adminId,
            Long employeeId,
            Long clientId,
            DailyReport request) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

        companyScope.requireManages(adminId, employee);

        // APPROVED is the status an employee must have to sign in, so it is also the
        // status that allows a report to be filed for them.
        if (!"APPROVED".equals(employee.getAccountStatus())) {
            throw new RuntimeException(
                    "Cannot file a report for an employee whose account is not approved and active");
        }

        return createForEmployee(employee, clientId, request);
    }

    // Submit daily report
    @Transactional
    public DailyReportResponseDTO createReport(
            Long employeeId,
            Long clientId,
            DailyReport request) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

        return createForEmployee(employee, clientId, request);
    }

    private DailyReportResponseDTO createForEmployee(
            Employee employee,
            Long clientId,
            DailyReport request) {

        Long employeeId = employee.getEmployeeId();

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() ->
                        new RuntimeException("Client not found"));

        // Employees and clients are both company data, so a report may reference a
        // client another administrator of the same company created.
        if (!sameCompany(employee, client)) {
            throw new RuntimeException(
                    "Access denied: Client does not belong to this company");
        }

        if (!"ACTIVE".equals(client.getStatus())) {
            throw new RuntimeException(
                    "Cannot submit report for inactive client");
        }

        validateReportDate(request.getReportDate(), employee);

        Integer profiles = request.getProfilesSubmitted() == null
                ? 0 : request.getProfilesSubmitted();

        Integer sourced = request.getCandidatesSourced() == null
                ? 0 : request.getCandidatesSourced();

        Integer contacted = request.getCandidatesContacted() == null
                ? 0 : request.getCandidatesContacted();

        Integer connected = request.getConnectedCount() == null
                ? 0 : request.getConnectedCount();

        validateCounts(profiles, sourced, contacted, connected);

        DailyReport report = new DailyReport();
        report.setEmployee(employee);
        report.setClient(client);
        report.setReportDate(request.getReportDate());
        report.setProfilesSubmitted(profiles);
        report.setCandidatesSourced(sourced);
        report.setCandidatesContacted(contacted);
        report.setConnectedCount(connected);
        report.setRemarks(request.getRemarks());
        applyPlatforms(report, request, sourced);

        return convertToDTO(reportRepository.save(report));
    }

    // Get employee's own reports
    @Transactional(readOnly = true)
    public List<DailyReportResponseDTO> getReportsByEmployee(
            Long employeeId) {

        return reportRepository
                .findByEmployeeEmployeeId(employeeId)
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    // Get employee report by ID
    @Transactional(readOnly = true)
    public DailyReportResponseDTO getEmployeeReport(
            Long reportId,
            Long employeeId) {

        DailyReport report = getReportById(reportId);

        if (!report.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Access denied");
        }

        return convertToDTO(report);
    }

    // Get all reports belonging to an admin
    @Transactional(readOnly = true)
    public List<DailyReportResponseDTO> getReportsByAdmin(
            Long adminId) {

        return reportRepository
                .findByEmployeeInCompanyOf(adminId)
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    // Get admin report by ID
    @Transactional(readOnly = true)
    public DailyReportResponseDTO getAdminReport(
            Long reportId,
            Long adminId) {

        DailyReport report = getReportById(reportId);

        if (!companyScope.manages(adminId, report.getEmployee())) {
            throw new RuntimeException("Access denied");
        }

        return convertToDTO(report);
    }

    // Get report entity by ID
    public DailyReport getReportById(Long id) {

        return reportRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Daily report not found"));
    }

    // Filter employee reports by date range
    @Transactional(readOnly = true)
    public List<DailyReportResponseDTO> getReportsByDateRange(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new RuntimeException("Both dates are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new RuntimeException(
                    "Start date cannot be after end date");
        }

        return reportRepository
                .findByEmployeeEmployeeIdAndReportDateBetween(
                        employeeId, startDate, endDate)
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    // Filter admin reports by date range
    @Transactional(readOnly = true)
    public List<DailyReportResponseDTO> getAdminReportsByDateRange(
            Long adminId,
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new RuntimeException("Both dates are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new RuntimeException(
                    "Start date cannot be after end date");
        }

        return reportRepository
                .findByEmployeeInCompanyOfAndReportDateBetween(
                        adminId, startDate, endDate)
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    // ADMIN: EXPORT THE REPORTS TABLE (CSV or Excel)
    @Transactional(readOnly = true)
    public SheetBuilder exportReports(
            Long adminId,
            String query,
            Long clientId,
            LocalDate reportDate) {

        SheetBuilder sheet = SheetBuilder.of("Daily reports",
                "Employee ID",
                "Employee",
                "Designation",
                "Date",
                "Client",
                "Candidates sourced",
                "Profiles submitted",
                "Candidates contacted",
                "Connected",
                "By platform",
                "Remarks");

        for (DailyReportResponseDTO row : getReportsByAdmin(adminId)) {

            if (clientId != null && !clientId.equals(row.getClientId())) {
                continue;
            }
            if (reportDate != null && !reportDate.equals(row.getReportDate())) {
                continue;
            }
            if (!TextFilter.contains(query,
                    row.getEmployeeName(),
                    row.getEmployeeCode(),
                    row.getClientName(),
                    row.getRemarks())) {
                continue;
            }

            sheet.row(
                    row.getEmployeeCode(),
                    row.getEmployeeName(),
                    row.getEmployeeDesignation(),
                    row.getReportDate(),
                    row.getClientName(),
                    row.getCandidatesSourced(),
                    row.getProfilesSubmitted(),
                    row.getCandidatesContacted(),
                    row.getConnectedCount(),
                    platformSummary(row.getPlatformCounts()),
                    row.getRemarks());
        }

        return sheet;
    }

    /** "Naukri 3, Apna 2" - the per-platform split of one report, kept in one cell. */
    private String platformSummary(List<PlatformCountDTO> counts) {

        if (counts == null || counts.isEmpty()) {
            return null;
        }

        return counts.stream()
                .filter(count -> count.profileCount() != null && count.profileCount() > 0)
                .map(count -> count.platform() + " " + count.profileCount())
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    // Update employee's own report
    @Transactional
    public DailyReportResponseDTO updateReport(
            Long reportId,
            Long employeeId,
            Long clientId,
            DailyReport request) {

        DailyReport existing = reportRepository.findById(reportId)
                .orElseThrow(() ->
                        new RuntimeException("Daily report not found"));

        if (!existing.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Access denied");
        }

        if (clientId != null) {

            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() ->
                            new RuntimeException("Client not found"));

            if (!sameCompany(existing.getEmployee(), client)) {
                throw new RuntimeException("Access denied");
            }

            if (!"ACTIVE".equals(client.getStatus())) {
                throw new RuntimeException("Client is inactive");
            }

            existing.setClient(client);
        }

        LocalDate newDate = request.getReportDate() != null
                ? request.getReportDate()
                : existing.getReportDate();

        validateReportDate(newDate, existing.getEmployee());

        Integer profiles = request.getProfilesSubmitted() != null
                ? request.getProfilesSubmitted()
                : existing.getProfilesSubmitted();

        Integer sourced = request.getCandidatesSourced() != null
                ? request.getCandidatesSourced()
                : existing.getCandidatesSourced();

        Integer contacted = request.getCandidatesContacted() != null
                ? request.getCandidatesContacted()
                : existing.getCandidatesContacted();

        Integer connected = request.getConnectedCount() != null
                ? request.getConnectedCount()
                : existing.getConnectedCount();

        validateCounts(profiles, sourced, contacted, connected);

        existing.setReportDate(newDate);
        existing.setProfilesSubmitted(profiles);
        existing.setCandidatesSourced(sourced);
        existing.setCandidatesContacted(contacted);
        existing.setConnectedCount(connected);

        if (request.getRemarks() != null) {
            existing.setRemarks(request.getRemarks());
        }

        applyPlatforms(existing, request, sourced);

        return convertToDTO(reportRepository.save(existing));
    }

    // Delete employee's own report
    @Transactional
    public void deleteReport(Long reportId, Long employeeId) {

        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() ->
                        new RuntimeException("Daily report not found"));

        if (!report.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Access denied");
        }

        reportRepository.delete(report);
    }
}
