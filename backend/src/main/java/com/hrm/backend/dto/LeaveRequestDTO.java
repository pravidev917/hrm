package com.hrm.backend.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record LeaveRequestDTO(

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @NotBlank(message = "Leave type is required")
        @Size(max = 50, message = "Leave type cannot exceed 50 characters")
        String type,

        @NotBlank(message = "Leave reason is required")
        @Size(max = 2000, message = "Reason cannot exceed 2000 characters")
        String reason,

        @Size(max = 20, message = "Half-day period cannot exceed 20 characters")
        String halfDayPeriod
) {
}