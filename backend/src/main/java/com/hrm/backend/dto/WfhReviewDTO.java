package com.hrm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WfhReviewDTO(

        @NotBlank(message = "Decision is required")
        String status,

        @Size(max = 2000, message = "Comment cannot exceed 2000 characters")
        String comment
) {
}
