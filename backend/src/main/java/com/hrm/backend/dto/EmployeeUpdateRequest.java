package com.hrm.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Editable fields of an employee record.
 *
 * Deliberately excludes accountStatus and admin: status has dedicated
 * activate/deactivate operations and ownership is set when the administrator
 * creates the account. The password is optional and only replaced when supplied.
 */
@Getter
@Setter
public class EmployeeUpdateRequest {

    @NotBlank(message = "Employee name is required")
    @Size(max = 100, message = "Employee name cannot exceed 100 characters")
    private String employeeName;

    /** Admin-assigned employee ID. Unique when present. */
    @Size(max = 40, message = "Employee ID cannot exceed 40 characters")
    private String employeeCode;

    @NotBlank(message = "Job designation is required")
    @Size(max = 50, message = "Job designation cannot exceed 50 characters")
    private String role;

    @Size(max = 50, message = "Contact cannot exceed 50 characters")
    private String contact;

    /** Optional: admins may correct an employee's email. Unique across accounts. */
    @Email(message = "Enter a valid email")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    @Size(max = 100, message = "Department cannot exceed 100 characters")
    private String department;

    @DecimalMin(value = "0.0", message = "Salary cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Enter a valid salary")
    private BigDecimal salary;

    /** Optional: when present, replaces the employee's password. */
    @Size(min = 8, message = "Password must contain at least 8 characters")
    private String password;

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