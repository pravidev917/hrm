package com.hrm.backend.service;

import com.hrm.backend.dto.AttendanceResponseDTO;
import com.hrm.backend.entity.Attendance;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AttendanceRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AttendanceService {

    private final AttendanceRepository repository;
    private final EmployeeService employeeService;
    private final AdminService adminService;

    public AttendanceService(
            AttendanceRepository repository,
            EmployeeService employeeService,
            AdminService adminService) {

        this.repository = repository;
        this.employeeService = employeeService;
        this.adminService = adminService;
    }

    // ================= EMPLOYEE =================

    @Transactional
    public AttendanceResponseDTO markAttendance(
            Long employeeId,
            String status) {

        Employee employee = getApprovedEmployee(employeeId);

        LocalDate today = LocalDate.now();

        if (repository
                .findByEmployeeEmployeeIdAndAttendanceDate(
                        employeeId, today)
                .isPresent()) {

            throw new RuntimeException(
                    "Attendance already marked for today");
        }

        String normalizedStatus = validateStatus(status);

        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(today);
        attendance.setStatus(normalizedStatus);

        try {
            return toDTO(repository.save(attendance));
        } catch (DataIntegrityViolationException ex) {
            // Protect against concurrent duplicate requests.
            throw new RuntimeException(
                    "Attendance already marked for today");
        }
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getEmployeeAttendance(
            Long employeeId) {

        getApprovedEmployee(employeeId);

        return repository
                .findByEmployeeEmployeeId(employeeId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceResponseDTO getEmployeeAttendanceById(
            Long employeeId,
            Long attendanceId) {

        getApprovedEmployee(employeeId);

        Attendance attendance = repository.findById(attendanceId)
                .orElseThrow(() ->
                        new RuntimeException("Attendance not found"));

        if (!attendance.getEmployee()
                .getEmployeeId()
                .equals(employeeId)) {

            throw new RuntimeException(
                    "Access denied");
        }

        return toDTO(attendance);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getEmployeeAttendanceByDateRange(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate) {

        getApprovedEmployee(employeeId);

        validateDateRange(startDate, endDate);

        return repository
                .findByEmployeeEmployeeIdAndAttendanceDateBetween(
                        employeeId,
                        startDate,
                        endDate)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public AttendanceResponseDTO updateEmployeeAttendance(
            Long employeeId,
            Long attendanceId,
            String status) {

        getApprovedEmployee(employeeId);

        Attendance attendance = repository.findById(attendanceId)
                .orElseThrow(() ->
                        new RuntimeException("Attendance not found"));

        if (!attendance.getEmployee()
                .getEmployeeId()
                .equals(employeeId)) {

            throw new RuntimeException(
                    "Access denied");
        }

        LocalDate today = LocalDate.now();

        if (!attendance.getAttendanceDate().equals(today)) {
            throw new RuntimeException(
                    "Only today's attendance can be updated");
        }

        attendance.setStatus(validateStatus(status));

        return toDTO(repository.save(attendance));
    }

    // ================= ADMIN =================

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getAdminAttendance(
            Long adminId) {

        getActiveAdmin(adminId);

        return repository
                .findByEmployeeAdminAdminId(adminId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceResponseDTO getAdminAttendanceById(
            Long adminId,
            Long attendanceId) {

        getActiveAdmin(adminId);

        Attendance attendance = repository.findById(attendanceId)
                .orElseThrow(() ->
                        new RuntimeException("Attendance not found"));

        validateAdminOwnership(adminId, attendance);

        return toDTO(attendance);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getAdminAttendanceByDateRange(
            Long adminId,
            LocalDate startDate,
            LocalDate endDate) {

        getActiveAdmin(adminId);

        validateDateRange(startDate, endDate);

        return repository
                .findByEmployeeAdminAdminIdAndAttendanceDateBetween(
                        adminId,
                        startDate,
                        endDate)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public AttendanceResponseDTO updateAdminAttendance(
            Long adminId,
            Long attendanceId,
            String status) {

        getActiveAdmin(adminId);

        Attendance attendance = repository.findById(attendanceId)
                .orElseThrow(() ->
                        new RuntimeException("Attendance not found"));

        validateAdminOwnership(adminId, attendance);

        LocalDate today = LocalDate.now();

        if (!attendance.getAttendanceDate().equals(today)) {
            throw new RuntimeException(
                    "Only today's attendance can be updated");
        }

        attendance.setStatus(validateStatus(status));

        return toDTO(repository.save(attendance));
    }

    @Transactional
    public void deleteAdminAttendance(
            Long adminId,
            Long attendanceId) {

        getActiveAdmin(adminId);

        Attendance attendance = repository.findById(attendanceId)
                .orElseThrow(() ->
                        new RuntimeException("Attendance not found"));

        validateAdminOwnership(adminId, attendance);

        repository.delete(attendance);
    }

    // ================= VALIDATION =================

    private String validateStatus(String status) {

        if (status == null || status.isBlank()) {
            throw new RuntimeException(
                    "Attendance status is required");
        }

        String normalized = status.trim().toUpperCase();

        if (!normalized.equals("PRESENT")
                && !normalized.equals("ABSENT")) {

            throw new RuntimeException(
                    "Attendance status must be PRESENT or ABSENT");
        }

        return normalized;
    }

    private void validateDateRange(
        LocalDate startDate,
        LocalDate endDate) {

    if (startDate == null || endDate == null) {
        throw new RuntimeException(
                "Start date and end date are required");
    }

    if (startDate.isAfter(endDate)) {
        throw new RuntimeException(
                "Start date cannot be after end date");
    }
}

    // ================= OWNERSHIP =================

    private Employee getApprovedEmployee(Long employeeId) {

        Employee employee = employeeService
                .getEmployeeById(employeeId);

        if (!"APPROVED".equalsIgnoreCase(
                employee.getAccountStatus())) {

            throw new RuntimeException(
                    "Employee account is not approved");
        }

        return employee;
    }

    private void getActiveAdmin(Long adminId) {

        adminService.getAdminById(adminId);
    }

    private void validateAdminOwnership(
            Long adminId,
            Attendance attendance) {

        Employee employee = attendance.getEmployee();

        if (employee.getAdmin() == null
                || employee.getAdmin().getAdminId() == null
                || !employee.getAdmin()
                        .getAdminId()
                        .equals(adminId)) {

            throw new RuntimeException(
                    "Access denied");
        }
    }

    // ================= DTO =================

    private AttendanceResponseDTO toDTO(
            Attendance attendance) {

        Employee employee = attendance.getEmployee();

        return new AttendanceResponseDTO(
                attendance.getAttendanceId(),
                employee.getEmployeeId(),
                employee.getEmployeeName(),
                attendance.getAttendanceDate(),
                attendance.getStatus());
    }
}