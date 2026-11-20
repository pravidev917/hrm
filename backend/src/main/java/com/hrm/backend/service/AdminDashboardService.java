package com.hrm.backend.service;

import com.hrm.backend.dto.AdminDashboardDTO;
import com.hrm.backend.dto.DailyReportDayTotals;
import com.hrm.backend.dto.PerformanceTrackerDTO;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveManagementRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Builds the administrator dashboard from counted database queries.
 *
 * This replaces the previous approach of loading full employee, leave, attendance and
 * client lists into the browser and counting there, which was several large requests
 * per page load and could silently disagree with the database.
 */
@Service
public class AdminDashboardService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveManagementRepository leaveRepository;
    private final ClientRepository clientRepository;
    private final DailyReportRepository dailyReportRepository;
    private final PerformanceTrackerService performanceTrackerService;
    private final AdminService adminService;
    private final CompanyTimeService companyTime;

    public AdminDashboardService(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            LeaveManagementRepository leaveRepository,
            ClientRepository clientRepository,
            DailyReportRepository dailyReportRepository,
            PerformanceTrackerService performanceTrackerService,
            AdminService adminService,
            CompanyTimeService companyTime) {

        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRepository = leaveRepository;
        this.clientRepository = clientRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.performanceTrackerService = performanceTrackerService;
        this.adminService = adminService;
        this.companyTime = companyTime;
    }

    @Transactional(readOnly = true)
    public AdminDashboardDTO getDashboard(String authenticatedEmail) {

        Long adminId = adminService.getAdminIdByEmail(authenticatedEmail);

        LocalDate today = companyTime.todayForCompany(adminService.requireCompanyId(adminId));
        LocalDate monthStart = today.withDayOfMonth(1);
        YearMonth currentMonth = YearMonth.from(today);

        // Team figures cover the whole company, not only the accounts this
        // administrator created.
        long total = employeeRepository.countInCompanyOf(adminId);
        long active = employeeRepository
                .countInCompanyOfAndAccountStatus(adminId, "APPROVED");
        long pending = employeeRepository
                .countInCompanyOfAndAccountStatus(adminId, "PENDING");
        long inactive = employeeRepository
                .countInCompanyOfAndAccountStatus(adminId, "INACTIVE")
                + employeeRepository.countInCompanyOfAndAccountStatus(adminId, "REJECTED");

        long markedToday = attendanceRepository
                .countByEmployeeInCompanyOfAndAttendanceDate(adminId, today);
        long presentToday = attendanceRepository
                .countByEmployeeInCompanyOfAndAttendanceDateAndStatus(adminId, today, "PRESENT");
        long absentToday = attendanceRepository
                .countByEmployeeInCompanyOfAndAttendanceDateAndStatus(adminId, today, "ABSENT");

        long pendingLeaves = leaveRepository
                .countByEmployeeInCompanyOfAndStatus(adminId, "PENDING");

        long requestsThisMonth = leaveRepository
                .countByEmployeeInCompanyOfAndStartDateGreaterThanEqual(adminId, monthStart);

        // Approved/rejected counts for the current month are derived from the same
        // review facts the leave list shows, restricted to leave that starts this month.
        List<LeaveManagement> monthLeaves = leaveRepository
                .findByEmployeeInCompanyOfAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        adminId, today.withDayOfMonth(today.lengthOfMonth()), monthStart);

        long approvedThisMonth = monthLeaves.stream()
                .filter(l -> "APPROVED".equals(l.getStatus()))
                .count();
        long rejectedThisMonth = monthLeaves.stream()
                .filter(l -> "REJECTED".equals(l.getStatus()))
                .count();

        // All-time totals for the dashboard leave card (same organisation scope).
        long leaveApproved = leaveRepository
                .countByEmployeeInCompanyOfAndStatus(adminId, "APPROVED");
        long leaveRejected = leaveRepository
                .countByEmployeeInCompanyOfAndStatus(adminId, "REJECTED");
        long leaveTotal = leaveRepository.countByEmployeeInCompanyOf(adminId);

        BigDecimal approvedDaysThisMonth = leaveRepository
                .sumApprovedDaysInCompanyOf(adminId, monthStart);

        List<AdminDashboardDTO.PendingLeave> queue = leaveRepository
                .findTop5PendingInCompanyOf(adminId, "PENDING", PageRequest.of(0, 5))
                .stream()
                .map(this::toPendingLeave)
                .toList();

        // Administrators create accounts themselves, so there is nothing to approve:
        // surface the newest team members instead.
        List<AdminDashboardDTO.RecentEmployee> recentTeam = employeeRepository
                .searchInCompanyOf(adminId, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 5,
                                org.springframework.data.domain.Sort.by("createdAt").descending()))
                .getContent()
                .stream()
                .map(this::toRecentEmployee)
                .toList();

        long totalClients = clientRepository.findByCompanyOf(adminId).size();
        long activeClients = clientRepository
                .findByCompanyOfAndStatus(adminId, "ACTIVE").size();

        // ---- daily reports card ----
        DailyReportDayTotals todayReports = dailyReportRepository.getCompanyDayTotals(adminId, today);
        DailyReportDayTotals monthReports = dailyReportRepository.getCompanyRangeTotals(adminId, monthStart, today);
        long reportsToday = todayReports == null ? 0 : todayReports.reportCount();
        long employeesSubmittedToday = todayReports == null ? 0 : todayReports.employeeCount();
        long employeesMissingToday = Math.max(active - employeesSubmittedToday, 0);

        AdminDashboardDTO.DailyReportSummary dailyReports =
                new AdminDashboardDTO.DailyReportSummary(
                        today,
                        reportsToday,
                        employeesSubmittedToday,
                        employeesMissingToday,
                        todayReports == null ? 0 : todayReports.profilesSubmitted(),
                        monthReports == null ? 0 : monthReports.reportCount(),
                        monthReports == null ? 0 : monthReports.profilesSubmitted());

        // ---- monthly performance card (weekly revenue of the current month) ----
        PerformanceTrackerDTO.RevenueReport revenue =
                performanceTrackerService.getMonthlyTracker(adminId, currentMonth.toString()).revenue();

        List<AdminDashboardDTO.MonthlyRevenueRow> revenueRows = revenue.employees().stream()
                .map(row -> new AdminDashboardDTO.MonthlyRevenueRow(
                        row.employeeId(),
                        row.employeeName(),
                        row.role(),
                        row.monthlyRevenue(),
                        row.weeksCounted()))
                .toList();

        PerformanceTrackerDTO.EmployeeRevenue top = revenue.employees().stream()
                .filter(row -> row.monthlyRevenue() != null && row.monthlyRevenue().signum() > 0)
                .findFirst()
                .orElse(null);

        AdminDashboardDTO.MonthlyPerformanceSummary monthlyPerformance =
                new AdminDashboardDTO.MonthlyPerformanceSummary(
                        currentMonth.toString(),
                        revenue.totalRevenue(),
                        revenue.employeesWithRevenue(),
                        revenue.weeksCounted(),
                        top == null ? null : top.employeeName(),
                        top == null ? BigDecimal.ZERO : top.monthlyRevenue(),
                        revenueRows);

        int presencePercent = active == 0
                ? 0
                : (int) Math.round(presentToday * 100d / active);

        return new AdminDashboardDTO(
                today,
                monthStart,
                currentMonth.toString(),
                new AdminDashboardDTO.PeopleSummary(total, active, pending, inactive),
                new AdminDashboardDTO.AttendanceSummary(
                        markedToday, presentToday, absentToday,
                        Math.max(active - markedToday, 0), false, presencePercent),
                new AdminDashboardDTO.LeaveSummary(
                        pendingLeaves,
                        leaveTotal,
                        leaveApproved,
                        leaveRejected,
                        approvedThisMonth,
                        rejectedThisMonth,
                        requestsThisMonth,
                        approvedDaysThisMonth == null ? BigDecimal.ZERO : approvedDaysThisMonth),
                new AdminDashboardDTO.ClientSummary(totalClients, activeClients),
                dailyReports,
                monthlyPerformance,
                queue,
                recentTeam
        );
    }

    private AdminDashboardDTO.PendingLeave toPendingLeave(LeaveManagement leave) {
        return new AdminDashboardDTO.PendingLeave(
                leave.getLeaveId(),
                leave.getEmployee().getEmployeeId(),
                leave.getEmployee().getEmployeeName(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getNumberOfDays(),
                leave.getType(),
                leave.getReason()
        );
    }

    private AdminDashboardDTO.RecentEmployee toRecentEmployee(Employee employee) {
        return new AdminDashboardDTO.RecentEmployee(
                employee.getEmployeeId(),
                employee.getEmployeeCode(),
                employee.getEmployeeName(),
                employee.getEmail(),
                employee.getRole(),
                employee.getDepartment(),
                employee.getAccountStatus()
        );
    }
}
