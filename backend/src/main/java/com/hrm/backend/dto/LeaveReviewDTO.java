package com.hrm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeaveReviewDTO(

        @NotBlank(message = "Status is required")
        String status,

        @Size(max = 2000, message = "Remarks cannot exceed 2000 characters")
        String remarks

) {
}