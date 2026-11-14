package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveResponseDTO(

        Long leaveId,

        Long employeeId,
        String employeeName,

        Long adminId,
        String adminName,

        LocalDate startDate,
        LocalDate endDate,

        BigDecimal numberOfDays,
        String halfDayPeriod,

        String type,
        String reason,
        String status,

        String adminRemarks,
        LocalDateTime reviewedAt

) {
}