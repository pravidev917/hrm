package com.hrm.backend.service;

import com.hrm.backend.dto.WeeklyReportRequestDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Client;
import com.hrm.backend.entity.Company;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.WeeklyReport;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.OfficeLocationRepository;
import com.hrm.backend.repository.WeeklyReportRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Weekly report rules.
 *
 * A week is a reporting period, not a lock: an employee may file several reports for the
 * same week (one per client, or one per candidate placed) and every report is kept, so the
 * duplicate checks that used to exist are gone.
 */
class WeeklyReportServiceTests {

    private WeeklyReportRepository repository;
    private WeeklyReportService service;
    private CompanyTimeService companyTime;
    private EmployeeRepository employeeRepository;
    private AdminRepository adminRepository;
    private ClientRepository clientRepository;
    private Employee employee;

    @BeforeEach
    void setUp() {
        repository = mock(WeeklyReportRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        adminRepository = mock(AdminRepository.class);
        clientRepository = mock(ClientRepository.class);

        companyTime = new CompanyTimeService(
                mock(OfficeLocationRepository.class), "Asia/Kolkata");

        service = new WeeklyReportService(
                repository, employeeRepository, adminRepository, clientRepository, companyTime);

        Company company = new Company();
        company.setCompanyId(1L);

        Admin admin = new Admin();
        admin.setAdminId(5L);
        admin.setStatus("ACTIVE");
        admin.setCompany(company);

        employee = new Employee();
        employee.setEmployeeId(10L);
        employee.setEmployeeName("Asha Rao");
        employee.setEmployeeCode("EMP010");
        employee.setRole("Software Engineer");
        employee.setAccountStatus("APPROVED");
        employee.setAdmin(admin);

        Client client = new Client();
        client.setClientId(20L);
        client.setClientName("Acme Corp");
        client.setStatus("ACTIVE");
        client.setAdmin(admin);

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(clientRepository.findById(20L)).thenReturn(Optional.of(client));
        when(adminRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(repository.save(any(WeeklyReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private WeeklyReportRequestDTO request(LocalDate date, int submitted, int revenue) {
        WeeklyReportRequestDTO request = new WeeklyReportRequestDTO();
        request.setReportDate(date);
        request.setClientId(20L);
        request.setProfilesSubmitted(submitted);
        request.setInterviewsScheduled(1);
        request.setFinalSelections(1);
        request.setJoined(0);
        request.setRevenueGenerated(BigDecimal.valueOf(revenue));
        return request;
    }

    /** A weekday inside the given week (weeks run Monday to Sunday). */
    private LocalDate inWeek(int weeksAgo, int offsetDays) {
        return LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(weeksAgo)
                .plusDays(offsetDays);
    }

    @Test
    void theSameEmployeeCanFileSeveralReportsForTheSameWeek() {
        LocalDate monday = inWeek(1, 0);

        service.createReport(10L, request(monday, 3, 100));
        service.createReport(10L, request(monday.plusDays(2), 2, 50));

        verify(repository, times(2)).save(any(WeeklyReport.class));
    }

    @Test
    void everyReportForTheSameWeekKeepsItsOwnWeekPeriod() {
        LocalDate monday = inWeek(1, 0);

        var first = service.createReport(10L, request(monday, 3, 100));
        var second = service.createReport(10L, request(monday.plusDays(3), 2, 50));

        assertEquals(monday, first.getWeekStartDate());
        assertEquals(monday, second.getWeekStartDate());
        assertEquals(monday.plusDays(6), first.getWeekEndDate());
        assertEquals(monday.plusDays(6), second.getWeekEndDate());
    }

    @Test
    void aFutureWeekIsStillRejected() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.createReport(10L, request(LocalDate.now().plusWeeks(1), 1, 10)));

        assertTrue(error.getMessage().toLowerCase().contains("future"));
    }

    /**
     * Same UTC-versus-IST trap as the daily report: the week containing the office's
     * "today" must be accepted while the server clock is still on yesterday.
     */
    @Test
    void theOfficeWeekIsNotRejectedWhenTheServerClockIsBehind() {
        CompanyTimeService officeClock = mock(CompanyTimeService.class);
        WeeklyReportService zonedService = new WeeklyReportService(
                repository, employeeRepository, adminRepository, clientRepository, officeClock);

        LocalDate officeToday = LocalDate.now().plusDays(1);
        when(officeClock.todayForEmployee(any(Employee.class))).thenReturn(officeToday);

        var report = zonedService.createReport(10L, request(officeToday, 2, 10));

        assertEquals(
                officeToday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                report.getWeekStartDate());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> zonedService.createReport(10L, request(officeToday.plusWeeks(1), 2, 10)));

        assertTrue(error.getMessage().toLowerCase().contains("future"));
    }
}
