
package com.hrm.backend.service;

import com.hrm.backend.dto.DailyReportResponseDTO;
import com.hrm.backend.entity.Client;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class DailyReportService {

    private final DailyReportRepository reportRepository;
    private final EmployeeRepository employeeRepository;
    private final ClientRepository clientRepository;

    public DailyReportService(
            DailyReportRepository reportRepository,
            EmployeeRepository employeeRepository,
            ClientRepository clientRepository) {

        this.reportRepository = reportRepository;
        this.employeeRepository = employeeRepository;
        this.clientRepository = clientRepository;
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
    }

    // Validate report date
    private void validateReportDate(LocalDate reportDate) {

        if (reportDate == null) {
            throw new RuntimeException("Report date is required");
        }

        if (reportDate.isAfter(LocalDate.now())) {
            throw new RuntimeException(
                    "Future report dates are not allowed");
        }
    }

    // Check duplicate report date
    private void validateDuplicateReport(
            Long employeeId,
            LocalDate reportDate,
            Long currentReportId) {

        Optional<DailyReport> existing =
                reportRepository
                        .findByEmployeeEmployeeIdAndReportDate(
                                employeeId, reportDate);

        if (existing.isPresent() &&
                !existing.get().getDailyReportId()
                        .equals(currentReportId)) {

            throw new RuntimeException(
                    "A report already exists for this employee on this date");
        }
    }

    // Convert entity into response DTO
    private DailyReportResponseDTO convertToDTO(DailyReport report) {

        return new DailyReportResponseDTO(
                report.getDailyReportId(),
                report.getEmployee().getEmployeeId(),
                report.getEmployee().getEmployeeName(),
                report.getClient().getClientId(),
                report.getClient().getClientName(),
                report.getProfilesSubmitted(),
                report.getCandidatesSourced(),
                report.getCandidatesContacted(),
                report.getConnectedCount(),
                report.getReportDate(),
                report.getRemarks()
        );
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

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() ->
                        new RuntimeException("Client not found"));

        if (!employee.getAdmin().getAdminId()
                .equals(client.getAdmin().getAdminId())) {
            throw new RuntimeException(
                    "Access denied: Client does not belong to your admin");
        }

        if (!"ACTIVE".equals(client.getStatus())) {
            throw new RuntimeException(
                    "Cannot submit report for inactive client");
        }

        validateReportDate(request.getReportDate());

        validateDuplicateReport(
                employeeId, request.getReportDate(), null);

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
                .findByEmployeeAdminAdminId(adminId)
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

        if (!report.getEmployee().getAdmin().getAdminId()
                .equals(adminId)) {
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
                .findByEmployeeAdminAdminIdAndReportDateBetween(
                        adminId, startDate, endDate)
                .stream()
                .map(this::convertToDTO)
                .toList();
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

            if (!existing.getEmployee().getAdmin().getAdminId()
                    .equals(client.getAdmin().getAdminId())) {
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

        validateReportDate(newDate);

        validateDuplicateReport(
                employeeId,
                newDate,
                existing.getDailyReportId());

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
