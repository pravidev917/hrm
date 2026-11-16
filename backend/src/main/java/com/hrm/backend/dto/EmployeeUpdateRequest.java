package com.hrm.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Editable fields of an employee record.
 *
 * Deliberately excludes password, accountStatus and admin: those must never be
 * changed through the profile edit endpoint (status has dedicated approve/reject/
 * activate/deactivate operations, ownership is set at registration).
 */
@Getter
@Setter
public class EmployeeUpdateRequest {

    @NotBlank(message = "Employee name is required")
    @Size(max = 100, message = "Employee name cannot exceed 100 characters")
    private String employeeName;

    @NotBlank(message = "Job designation is required")
    @Size(max = 50, message = "Job designation cannot exceed 50 characters")
    private String role;

    @Size(max = 50, message = "Contact cannot exceed 50 characters")
    private String contact;

    /** Optional: admins may correct an employee's email. Unique across accounts. */
    @Email(message = "Enter a valid email")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    /**
     * Optional: drives birthdays on the calendar. Must be in the past.
     * Sent as YYYY-MM-DD.
     */
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    /**
     * Optional: drives work anniversaries on the calendar.
     * Not in the future.
     */
    @PastOrPresent(message = "Joining date cannot be in the future")
    private LocalDate joiningDate;
}
