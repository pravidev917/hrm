package com.hrm.backend.dto;

import com.hrm.backend.entity.Employee;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Employee representation returned to administrators.
 * Includes the owning admin's name so the UI can show who manages the employee
 * without an extra request. Never contains the password hash.
 */
public record EmployeeResponseDTO(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String email,
        String role,
        String contact,
        String department,
        BigDecimal salary,
        String accountStatus,
        LocalDate dateOfBirth,
        LocalDate joiningDate,
        Long adminId,
        String adminName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EmployeeResponseDTO from(Employee employee) {
        return new EmployeeResponseDTO(
                employee.getEmployeeId(),
                employee.getEmployeeCode(),
                employee.getEmployeeName(),
                employee.getEmail(),
                employee.getRole(),
                employee.getContact(),
                employee.getDepartment(),
                employee.getSalary(),
                employee.getAccountStatus(),
                employee.getDateOfBirth(),
                employee.getJoiningDate(),
                employee.getAdmin() != null ? employee.getAdmin().getAdminId() : null,
                employee.getAdmin() != null ? employee.getAdmin().getAdminName() : null,
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}