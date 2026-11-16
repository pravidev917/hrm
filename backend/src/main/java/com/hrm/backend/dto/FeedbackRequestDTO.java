package com.hrm.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create/update payload for a sourced-candidate record.
 *
 * Only the candidate name is mandatory: sourcing is often captured at a call
 * before the CV, notice period or CTC are known, and forcing those fields would
 * make people enter fake values.
 */
@Getter
@Setter
@NoArgsConstructor
public class FeedbackRequestDTO {

    @NotBlank(message = "Candidate name is required")
    @Size(max = 100, message = "Candidate name cannot exceed 100 characters")
    private String candidateName;

    @Email(message = "Enter a valid email")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String candidateEmail;

    @Size(max = 30, message = "Contact number cannot exceed 30 characters")
    private String contactNumber;

    @Size(max = 100, message = "Current location cannot exceed 100 characters")
    private String currentLocation;

    @Size(max = 100, message = "Preferred location cannot exceed 100 characters")
    private String preferredLocation;

    @DecimalMin(value = "0.0", inclusive = true, message = "Current CTC cannot be negative")
    private BigDecimal currentCtc;

    @DecimalMin(value = "0.0", inclusive = true, message = "Expected CTC cannot be negative")
    private BigDecimal expectedCtc;

    @Size(max = 150, message = "Current company cannot exceed 150 characters")
    private String currentCompany;

    @Size(max = 100, message = "Current designation cannot exceed 100 characters")
    private String currentDesignation;

    @Min(value = 0, message = "Notice period cannot be negative")
    @Max(value = 365, message = "Notice period cannot exceed 365 days")
    private Integer noticePeriodDays;

    @Size(max = 100, message = "HR name cannot exceed 100 characters")
    private String hrName;

    @Size(max = 150, message = "Client name cannot exceed 150 characters")
    private String clientName;

    private Boolean interviewScheduled;

    private LocalDate interviewScheduledDate;

    private Boolean interviewAttended;

    /** SOURCED, INTERVIEW_SCHEDULED, INTERVIEW_ATTENDED, SELECTED, JOINED, REJECTED, DROPPED */
    @Pattern(
            regexp = "^(SOURCED|INTERVIEW_SCHEDULED|INTERVIEW_ATTENDED|SELECTED|JOINED|REJECTED|DROPPED)$",
            message = "Status must be one of: SOURCED, INTERVIEW_SCHEDULED, INTERVIEW_ATTENDED, SELECTED, JOINED, REJECTED, DROPPED"
    )
    private String status;

    private Boolean googleFeedbackGiven;

    @Size(max = 5000, message = "Comments cannot exceed 5000 characters")
    private String comments;
}
