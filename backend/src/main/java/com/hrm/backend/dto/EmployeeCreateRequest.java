package com.hrm.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Payload used by an administrator to create an employee account.
 * Accounts created this way are active immediately: there is no self-registration
 * and therefore no approval step.
 */
@Getter
@Setter
public class EmployeeCreateRequest {

    @NotBlank(message = "Employee name is required")
    @Size(max = 100, message = "Employee name cannot exceed 100 characters")
    private String employeeName;

    /** Admin-assigned employee ID, also used as a sign-in identifier. */
    @NotBlank(message = "Employee ID is required")
    @Size(max = 40, message = "Employee ID cannot exceed 40 characters")
    private String employeeCode;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    /** Phone number, also used as a sign-in identifier. */
    @NotBlank(message = "Phone number is required")
    @Size(max = 50, message = "Phone number cannot exceed 50 characters")
    private String contact;

    @NotBlank(message = "Department is required")
    @Size(max = 100, message = "Department cannot exceed 100 characters")
    private String department;

    @NotBlank(message = "Job designation is required")
    @Size(max = 50, message = "Job designation cannot exceed 50 characters")
    private String role;

    @DecimalMin(value = "0.0", message = "Salary cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Enter a valid salary")
    private BigDecimal salary;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String password;
}
