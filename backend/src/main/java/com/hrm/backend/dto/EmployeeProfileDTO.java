package com.hrm.backend.dto;

import java.time.LocalDateTime;

public record EmployeeProfileDTO(
        Long employeeId,
        String employeeName,
        String email,
        String role,
        String contact,
        String accountStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
