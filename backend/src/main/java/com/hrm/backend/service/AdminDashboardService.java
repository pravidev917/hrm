package com.hrm.backend.service;

import com.hrm.backend.dto.AdminDashboardDTO;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveManagementRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final AdminService adminService;

    public AdminDashboardService(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            LeaveManagementRepository leaveRepository,
            ClientRepository clientRepository,
            AdminService adminService) {

        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRepository = leaveRepository;
        this.clientRepository = clientRepository;
        this.adminService = adminService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardDTO getDashboard(String authenticatedEmail) {

        Long adminId = adminService.getAdminIdByEmail(authenticatedEmail);

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        long total = employeeRepository.countByAdminAdminId(adminId);
        long active = employeeRepository
                .countByAdminAdminIdAndAccountStatus(adminId, "APPROVED");
        long pending = employeeRepository
                .countByAdminAdminIdAndAccountStatus(adminId, "PENDING");
        long inactive = employeeRepository
                .countByAdminAdminIdAndAccountStatus(adminId, "INACTIVE")
                + employeeRepository.countByAdminAdminIdAndAccountStatus(adminId, "REJECTED");

        long markedToday = attendanceRepository
                .countByEmployeeAdminAdminIdAndAttendanceDate(adminId, today);
        long presentToday = attendanceRepository
                .countByEmployeeAdminAdminIdAndAttendanceDateAndStatus(adminId, today, "PRESENT");
        long absentToday = attendanceRepository
                .countByEmployeeAdminAdminIdAndAttendanceDateAndStatus(adminId, today, "ABSENT");

        long pendingLeaves = leaveRepository
                .countByEmployeeAdminAdminIdAndStatus(adminId, "PENDING");

        long requestsThisMonth = leaveRepository
                .countByEmployeeAdminAdminIdAndStartDateGreaterThanEqual(adminId, monthStart);

        // Approved/rejected counts for the current month are derived from the same
        // review facts the leave list shows, restricted to leave that starts this month.
        List<LeaveManagement> monthLeaves = leaveRepository
                .findByEmployeeAdminAdminIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        adminId, today.withDayOfMonth(today.lengthOfMonth()), monthStart);

        long approvedThisMonth = monthLeaves.stream()
                .filter(l -> "APPROVED".equals(l.getStatus()))
                .count();
        long rejectedThisMonth = monthLeaves.stream()
                .filter(l -> "REJECTED".equals(l.getStatus()))
                .count();

        BigDecimal approvedDaysThisMonth = leaveRepository
                .sumApprovedDaysByAdminSince(adminId, monthStart);

        List<AdminDashboardDTO.PendingLeave> queue = leaveRepository
                .findTop5ByEmployeeAdminAdminIdAndStatusOrderByStartDateAsc(adminId, "PENDING")
                .stream()
                .map(this::toPendingLeave)
                .toList();

        // Administrators create accounts themselves, so there is nothing to approve:
        // surface the newest team members instead.
        List<AdminDashboardDTO.RecentEmployee> recentTeam = employeeRepository
                .searchByAdmin(adminId, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 5,
                                org.springframework.data.domain.Sort.by("createdAt").descending()))
                .getContent()
                .stream()
                .map(this::toRecentEmployee)
                .toList();

        long totalClients = clientRepository.findByAdminAdminId(adminId).size();
        long activeClients = clientRepository
                .findByAdminAdminIdAndStatusOrderByClientNameAsc(adminId, "ACTIVE").size();

        return new AdminDashboardDTO(
                today,
                monthStart,
                new AdminDashboardDTO.PeopleSummary(total, active, pending, inactive),
                new AdminDashboardDTO.AttendanceSummary(
                        markedToday, presentToday, absentToday, Math.max(active - markedToday, 0)),
                new AdminDashboardDTO.LeaveSummary(
                        pendingLeaves, approvedThisMonth, rejectedThisMonth, requestsThisMonth,
                        approvedDaysThisMonth == null ? BigDecimal.ZERO : approvedDaysThisMonth),
                new AdminDashboardDTO.ClientSummary(totalClients, activeClients),
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
