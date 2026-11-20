package com.hrm.backend.service;

import com.hrm.backend.dto.ReportingOverviewDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Client;
import com.hrm.backend.entity.Company;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.entity.DailyReportPlatformCount;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeSalaryHistory;
import com.hrm.backend.entity.Feedback;
import com.hrm.backend.entity.WeeklyReport;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.EmployeeSalaryHistoryRepository;
import com.hrm.backend.repository.FeedbackRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.WeeklyReportRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reporting rules that must not drift: salary x 3 targets, client budgets vs
 * revenue, and source de-duplication between candidate records and daily reports.
 */
class ReportingServiceTest {

    private EmployeeRepository employeeRepository;
    private ClientRepository clientRepository;
    private DailyReportRepository dailyReportRepository;
    private WeeklyReportRepository weeklyReportRepository;
    private FeedbackRepository feedbackRepository;
    private LeaveManagementRepository leaveRepository;
    private EmployeeSalaryHistoryRepository salaryRepository;
    private CompanyScopeService companyScope;
    private CompanyTimeService companyTime;
    private ReportingService service;

    private Company company;
    private Admin admin;
    private Employee alice;
    private Employee bob;
    private Client client;

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        clientRepository = mock(ClientRepository.class);
        dailyReportRepository = mock(DailyReportRepository.class);
        weeklyReportRepository = mock(WeeklyReportRepository.class);
        feedbackRepository = mock(FeedbackRepository.class);
        leaveRepository = mock(LeaveManagementRepository.class);
        salaryRepository = mock(EmployeeSalaryHistoryRepository.class);
        companyScope = mock(CompanyScopeService.class);
        companyTime = mock(CompanyTimeService.class);

        service = new ReportingService(
                employeeRepository, clientRepository, dailyReportRepository,
                weeklyReportRepository, feedbackRepository, leaveRepository,
                salaryRepository, companyScope, companyTime);

        company = new Company();
        company.setCompanyId(1L);
        admin = new Admin();
        admin.setAdminId(10L);
        admin.setCompany(company);

        alice = employee(1L, "Alice", admin);
        bob = employee(2L, "Bob", admin);

        client = new Client();
        client.setClientId(20L);
        client.setAdmin(admin);
        client.setClientName("Acme");
        client.setPosition("Developer");
        client.setNumberOfPositions(2);
        client.setBudgetPerCandidate(new BigDecimal("500.00"));
        client.setSalaryOffered(new BigDecimal("50000.00"));
        client.setPercentagePerCandidate(new BigDecimal("55.00"));

        when(companyScope.companyIdOf(10L)).thenReturn(1L);
        when(companyTime.zoneForCompany(1L)).thenReturn(ZoneId.of("UTC"));
        when(employeeRepository.findByCompanyOf(10L)).thenReturn(List.of(alice, bob));
        when(employeeRepository.findById(1L)).thenReturn(java.util.Optional.of(alice));
        when(clientRepository.findByCompanyOf(10L)).thenReturn(List.of(client));
        when(leaveRepository
                .findByEmployeeInCompanyOfAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        eq(10L), any(), any()))
                .thenReturn(List.of());
        when(salaryRepository.findCompanyHistoryUpTo(eq(10L), any()))
                .thenReturn(List.of(salary(alice, "2026-01-01", "1000.00")));
    }

    @Test
    void countsEachCandidateOnceAndKeepsBudgetSeparateFromRevenue() {
        DailyReport aliceReport = report(alice, "2026-01-05", 3);
        aliceReport.setSourcingPlatform("LinkedIn");
        aliceReport.getPlatformCounts().add(platform(aliceReport, "LinkedIn", 3));

        DailyReport bobReport = report(bob, "2026-01-06", 4);
        bobReport.setSourcingPlatform("Naukri");
        bobReport.getPlatformCounts().add(platform(bobReport, "Naukri", 4));

        Feedback joined = candidate(alice, "2026-01-07", "LinkedIn", "JOINED");
        Feedback sourced = candidate(alice, "2026-01-08", "LinkedIn", "SOURCED");
        Feedback outsideRange = candidate(alice, "2025-12-31", "LinkedIn", "JOINED");

        WeeklyReport week = new WeeklyReport();
        week.setEmployee(alice);
        week.setClient(client);
        week.setWeekStartDate(LocalDate.parse("2026-01-05"));
        week.setRevenueGenerated(new BigDecimal("1000.00"));

        when(dailyReportRepository
                .findByEmployeeInCompanyOfAndReportDateBetween(eq(10L), any(), any()))
                .thenReturn(List.of(aliceReport, bobReport));
        when(weeklyReportRepository
                .findByEmployeeInCompanyOfAndWeekStartDateBetween(eq(10L), any(), any()))
                .thenReturn(List.of(week));
        when(feedbackRepository.findByEmployeeInCompanyOf(10L))
                .thenReturn(List.of(joined, sourced, outsideRange));

        ReportingOverviewDTO result = service.forAdmin(
                10L, "2026-01-01", "2026-01-31", null, null, null, null);

        // Alice has candidate records, so her report's 3 is not added to her 2 candidates.
        // Bob has no candidate records, so his report contributes 4.
        assertThat(result.sourcedProfiles().totalSourced()).isEqualTo(6);
        assertThat(result.sourcedProfiles().byPlatform())
                .anySatisfy(row -> {
                    assertThat(row.platform()).isEqualTo("LinkedIn");
                    assertThat(row.sourcedProfiles()).isEqualTo(2);
                })
                .anySatisfy(row -> {
                    assertThat(row.platform()).isEqualTo("Naukri");
                    assertThat(row.sourcedProfiles()).isEqualTo(4);
                });

        // Salary 1,000 x 3, revenue 1,000.
        assertThat(result.revenueTargets().totalTarget()).isEqualByComparingTo("3000.00");
        assertThat(result.revenueTargets().totalRevenue()).isEqualByComparingTo("1000.00");
        assertThat(result.revenueTargets().employees())
                .filteredOn(row -> row.employeeId().equals(1L))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.targetConfigured()).isTrue();
                    assertThat(row.rawAchievementPercent()).isEqualByComparingTo("33.33");
                });
        assertThat(result.revenueTargets().employees())
                .filteredOn(row -> row.employeeId().equals(2L))
                .singleElement()
                .satisfies(row -> assertThat(row.targetConfigured()).isFalse());

        // 2 openings x 500 budget = 1,000 budget, exactly 1,000 attributable revenue.
        assertThat(result.clientBudgets().totalBudget()).isEqualByComparingTo("1000.00");
        assertThat(result.clientBudgets().totalRevenue()).isEqualByComparingTo("1000.00");
        assertThat(result.clientBudgets().clients()).singleElement()
                .satisfies(row -> {
                    assertThat(row.budgetConfigured()).isTrue();
                    assertThat(row.remainingBudget()).isEqualByComparingTo("0.00");
                    assertThat(row.employees()).singleElement()
                            .satisfies(contribution ->
                                    assertThat(contribution.revenue()).isEqualByComparingTo("1000.00"));
                });

        assertThat(result.platformEffectiveness())
                .filteredOn(row -> row.platform().equals("LinkedIn"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.sourcedProfiles()).isEqualTo(2);
                    assertThat(row.outcomeTracked()).isEqualTo(2);
                    assertThat(row.joined()).isEqualTo(1);
                    assertThat(row.conversionRate()).isEqualByComparingTo("50.00");
                });

        assertThat(result.dailyReports().submittedReports()).isEqualTo(2);
        assertThat(result.dailyReports().sourcedProfiles()).isEqualTo(7);
    }

    @Test
    void targetUsesTheSalaryEffectiveInEachMonth() {
        when(salaryRepository.findCompanyHistoryUpTo(eq(10L), any()))
                .thenReturn(List.of(
                        salary(alice, "2026-01-01", "1000.00"),
                        salary(alice, "2026-02-01", "2000.00")));

        ReportingOverviewDTO result = service.forAdmin(
                10L, "2026-01-01", "2026-02-28", 1L, null, null, null);

        // January keeps 1,000 x 3 and February uses 2,000 x 3: 9,000 total.
        assertThat(result.revenueTargets().totalTarget()).isEqualByComparingTo("9000.00");
        assertThat(result.revenueTargets().employees()).singleElement()
                .satisfies(row -> {
                    assertThat(row.targetConfigured()).isTrue();
                    assertThat(row.monthsCounted()).isEqualTo(2);
                });
    }

    @Test
    void employeeViewIsScopedToThatEmployee() {
        when(dailyReportRepository
                .findByEmployeeInCompanyOfAndReportDateBetween(eq(10L), any(), any()))
                .thenReturn(List.of(report(alice, "2026-01-05", 2)));
        when(feedbackRepository.findByEmployeeInCompanyOf(10L)).thenReturn(List.of(
                candidate(alice, "2026-01-06", "LinkedIn", "JOINED"),
                candidate(bob, "2026-01-06", "LinkedIn", "JOINED")));

        ReportingOverviewDTO result = service.forEmployee(
                1L, "2026-01-01", "2026-01-31", null, null, null);

        assertThat(result.filters().employeeId()).isEqualTo(1L);
        assertThat(result.sourcedProfiles().byEmployee()).singleElement()
                .satisfies(row -> assertThat(row.employeeId()).isEqualTo(1L));
        assertThat(result.recentCandidates()).singleElement()
                .satisfies(row -> assertThat(row.employeeId()).isEqualTo(1L));
        assertThat(result.revenueTargets().employees()).singleElement()
                .satisfies(row -> assertThat(row.employeeId()).isEqualTo(1L));
    }

    private Employee employee(Long id, String name, Admin admin) {
        Employee employee = new Employee();
        employee.setEmployeeId(id);
        employee.setEmployeeName(name);
        employee.setAdmin(admin);
        return employee;
    }

    private DailyReport report(Employee employee, String date, int sourced) {
        DailyReport report = new DailyReport();
        report.setEmployee(employee);
        report.setClient(client);
        report.setReportDate(LocalDate.parse(date));
        report.setCandidatesSourced(sourced);
        report.setProfilesSubmitted(sourced);
        report.setCandidatesContacted(sourced);
        report.setConnectedCount(0);
        return report;
    }

    private DailyReportPlatformCount platform(DailyReport report, String name, int count) {
        DailyReportPlatformCount row = new DailyReportPlatformCount();
        row.setDailyReport(report);
        row.setPlatform(name);
        row.setProfileCount(count);
        return row;
    }

    private Feedback candidate(Employee employee, String date, String platform, String status) {
        Feedback feedback = new Feedback();
        feedback.setFeedbackId((long) (Math.random() * 100000));
        feedback.setEmployee(employee);
        feedback.setCandidateName("Candidate " + status);
        feedback.setSourcedDate(LocalDate.parse(date));
        feedback.setSourcingPlatform(platform);
        feedback.setStatus(status);
        return feedback;
    }

    private EmployeeSalaryHistory salary(Employee employee, String month, String amount) {
        EmployeeSalaryHistory history = new EmployeeSalaryHistory();
        history.setEmployee(employee);
        history.setEffectiveMonth(LocalDate.parse(month));
        history.setSalary(new BigDecimal(amount));
        return history;
    }
}
