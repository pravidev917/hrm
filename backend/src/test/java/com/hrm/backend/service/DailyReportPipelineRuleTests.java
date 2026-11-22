package com.hrm.backend.service;

import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Client;
import com.hrm.backend.entity.Company;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.DailyReportPlatformCount;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Daily report rules: the sourcing pipeline, platform reconciliation and the
 * platform names that reach the database.
 *
 * The pipeline rule is the one users notice: "candidates sourced" is the pool every
 * later stage is drawn from, so no single later stage may be larger than it.
 */
class DailyReportPipelineRuleTests {

    private DailyReportRepository reportRepository;
    private EmployeeRepository employeeRepository;
    private ClientRepository clientRepository;
    private DailyReportService service;
    private Employee employee;
    private Client client;

    @BeforeEach
    void setUp() {
        reportRepository = mock(DailyReportRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        clientRepository = mock(ClientRepository.class);

        service = new DailyReportService(
                reportRepository, employeeRepository, clientRepository, mock(CompanyScopeService.class));

        Company company = new Company();
        company.setCompanyId(1L);

        Admin admin = new Admin();
        admin.setCompany(company);

        employee = new Employee();
        employee.setEmployeeId(10L);
        employee.setEmployeeName("Asha Rao");
        employee.setEmployeeCode("EMP010");
        employee.setRole("Software Engineer");
        employee.setAccountStatus("APPROVED");
        employee.setAdmin(admin);

        client = new Client();
        client.setClientId(20L);
        client.setClientName("Acme Corp");
        client.setStatus("ACTIVE");
        client.setAdmin(admin);

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(clientRepository.findById(20L)).thenReturn(Optional.of(client));
        when(reportRepository.findByEmployeeEmployeeIdAndReportDate(any(), any()))
                .thenReturn(Optional.empty());
        when(reportRepository.save(any(DailyReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private DailyReport report(int sourced, int submitted, int contacted, int connected) {
        DailyReport report = new DailyReport();
        report.setReportDate(LocalDate.now().minusDays(1));
        report.setCandidatesSourced(sourced);
        report.setProfilesSubmitted(submitted);
        report.setCandidatesContacted(contacted);
        report.setConnectedCount(connected);
        return report;
    }

    private DailyReport withPlatform(DailyReport report, String platform, int count) {
        DailyReportPlatformCount row = new DailyReportPlatformCount();
        row.setPlatform(platform);
        row.setProfileCount(count);
        List<DailyReportPlatformCount> rows = new ArrayList<>();
        rows.add(row);
        report.setPlatformCounts(rows);
        return report;
    }

    @Test
    void aNormalFunnelIsAccepted() {
        // 20 sourced / 15 submitted / 10 contacted / 8 connected is a real funnel.
        var saved = service.createReport(10L, 20L, report(20, 15, 10, 8));
        assertEquals(20, saved.getCandidatesSourced());
        assertEquals(15, saved.getProfilesSubmitted());
    }

    @Test
    void submittedCannotExceedSourced() {
        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.createReport(10L, 20L, report(10, 15, 0, 0)));

        assertTrue(error.getMessage().contains("cannot be more than candidates sourced"));
        verify(reportRepository, never()).save(any(DailyReport.class));
    }

    @Test
    void contactedCannotExceedSourced() {
        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.createReport(10L, 20L, report(5, 0, 6, 0)));

        assertTrue(error.getMessage().contains("cannot be more than candidates sourced"));
    }

    @Test
    void connectedCannotExceedSourced() {
        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.createReport(10L, 20L, report(5, 0, 10, 8)));

        assertTrue(error.getMessage().contains("cannot be more than candidates sourced"));
    }

    @Test
    void connectedStillCannotExceedContacted() {
        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.createReport(10L, 20L, report(20, 0, 3, 5)));

        assertTrue(error.getMessage().contains("cannot exceed candidates contacted"));
    }

    @Test
    void platformCountsMustAddUpToSourced() {
        DailyReport request = withPlatform(report(10, 4, 2, 1), "Naukri", 7);

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.createReport(10L, 20L, request));

        assertTrue(error.getMessage().contains("They must match"));
    }

    @Test
    void retiredPlatformNamesAreFoldedIntoTheCanonicalOnes() {
        DailyReport request = withPlatform(report(6, 2, 1, 1), "Indeed", 6);

        var saved = service.createReport(10L, 20L, request);

        assertEquals("Apna", saved.getSourcingPlatform());
        assertEquals("Apna", saved.getPlatformCounts().get(0).platform());
    }

    @Test
    void unknownPlatformNamesAreRejectedWithTheAllowedList() {
        DailyReport request = withPlatform(report(3, 1, 0, 0), "Monster", 3);

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.createReport(10L, 20L, request));

        assertTrue(error.getMessage().contains("Unknown sourcing platform"));
        assertTrue(error.getMessage().contains("Naukri, Apna, LinkedIn, Careers, Others"));
    }

    @Test
    void anInactiveEmployeeCannotHaveAReportFiledByAnAdministrator() {
        employee.setAccountStatus("PENDING");

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.createReportForEmployee(1L, 10L, 20L, report(5, 2, 1, 1)));

        assertTrue(error.getMessage().contains("not approved and active"));
    }
}
