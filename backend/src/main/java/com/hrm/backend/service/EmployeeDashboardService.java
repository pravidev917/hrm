package com.hrm.backend.service;

import com.hrm.backend.dto.DailyReportTotals;
import com.hrm.backend.dto.DashboardDTO;
import com.hrm.backend.dto.EmployeeProfileDTO;
import com.hrm.backend.dto.WeeklyReportTotals;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.WeeklyReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class EmployeeDashboardService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveManagementRepository leaveRepository;
    private final DailyReportRepository dailyReportRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final CompanyTimeService companyTime;

    public EmployeeDashboardService(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            LeaveManagementRepository leaveRepository,
            DailyReportRepository dailyReportRepository,
            WeeklyReportRepository weeklyReportRepository,
            CompanyTimeService companyTime) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRepository = leaveRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.weeklyReportRepository = weeklyReportRepository;
        this.companyTime = companyTime;
    }

    @Transactional(readOnly = true)
    public EmployeeProfileDTO getOwnProfile(String authenticatedEmail) {
        Employee employee = getApprovedEmployee(authenticatedEmail);
        return toProfileDTO(employee);
    }

    @Transactional(readOnly = true)
    public DashboardDTO getOwnDashboard(String authenticatedEmail) {
        Employee employee = getApprovedEmployee(authenticatedEmail);
        Long employeeId = employee.getEmployeeId();
        LocalDate today = companyTime.todayForEmployee(employee);
        LocalDate monthStart = today.withDayOfMonth(1);

        String todayStatus = attendanceRepository
                .findByEmployeeEmployeeIdAndAttendanceDate(employeeId, today)
                .map(attendance -> attendance.getStatus())
                .orElse("NOT_MARKED");

        long present = attendanceRepository
                .countByEmployeeEmployeeIdAndAttendanceDateBetweenAndStatus(
                        employeeId, monthStart, today, "PRESENT");
        long absent = attendanceRepository
                .countByEmployeeEmployeeIdAndAttendanceDateBetweenAndStatus(
                        employeeId, monthStart, today, "ABSENT");
        long recorded = attendanceRepository
                .countByEmployeeEmployeeIdAndAttendanceDateBetween(
                        employeeId, monthStart, today);

        DashboardDTO.AttendanceSummary attendance =
                new DashboardDTO.AttendanceSummary(
                        todayStatus, present, absent, recorded);

        DashboardDTO.LeaveSummary leaves = new DashboardDTO.LeaveSummary(
                leaveRepository.countByEmployeeEmployeeIdAndStatus(employeeId, "PENDING"),
                leaveRepository.countByEmployeeEmployeeIdAndStatus(employeeId, "APPROVED"),
                leaveRepository.countByEmployeeEmployeeIdAndStatus(employeeId, "REJECTED"),
                leaveRepository.countByEmployeeEmployeeIdAndStatus(employeeId, "CANCELLED"),
                nullToZero(leaveRepository.sumApprovedDaysByEmployeeId(employeeId)));

        DailyReportTotals daily = dailyReportRepository.getDashboardTotals(employeeId);
        DashboardDTO.DailyReportSummary dailySummary =
                new DashboardDTO.DailyReportSummary(
                        safe(daily.reportCount()), safe(daily.profilesSubmitted()),
                        safe(daily.candidatesSourced()), safe(daily.candidatesContacted()),
                        safe(daily.connectedCount()));

        WeeklyReportTotals weekly = weeklyReportRepository.getDashboardTotals(employeeId);
        DashboardDTO.WeeklyReportSummary weeklySummary =
                new DashboardDTO.WeeklyReportSummary(
                        safe(weekly.reportCount()), safe(weekly.profilesSubmitted()),
                        safe(weekly.interviewsScheduled()), safe(weekly.finalSelections()),
                        safe(weekly.joined()), nullToZero(weekly.revenueGenerated()));

        return new DashboardDTO(
                today,
                toProfileDTO(employee),
                attendance,
                leaves,
                dailySummary,
                weeklySummary);
    }

    private Employee getApprovedEmployee(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Authenticated employee email is missing");
        }

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Employee account not found"));

        if (!"APPROVED".equalsIgnoreCase(employee.getAccountStatus())) {
            throw new IllegalStateException("Employee account is not approved");
        }

        return employee;
    }

    private EmployeeProfileDTO toProfileDTO(Employee employee) {
        return new EmployeeProfileDTO(
                employee.getEmployeeId(),
                employee.getEmployeeName(),
                employee.getEmail(),
                employee.getRole(),
                employee.getContact(),
                employee.getAccountStatus(),
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
