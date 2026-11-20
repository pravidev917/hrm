package com.hrm.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record WfhResponseDTO(
        Long wfhId,
        Long employeeId,
        String employeeName,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String status,
        Long reviewedByAdminId,
        String reviewedByName,
        String adminComment,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        boolean coversToday
) {
}
