
package com.hrm.backend.dto;

import com.hrm.backend.entity.Employee;
import java.time.LocalDateTime;

public record EmployeeResponseDTO(
        Long employeeId,
        String employeeName,
        String email,
        String role,
        String contact,
        String accountStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EmployeeResponseDTO from(Employee employee) {
        return new EmployeeResponseDTO(
                employee.getEmployeeId(),
                employee.getEmployeeName(),
                employee.getEmail(),
                employee.getRole(),
                employee.getContact(),
                employee.getAccountStatus(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
