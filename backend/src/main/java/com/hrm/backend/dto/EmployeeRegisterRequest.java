package com.hrm.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeRegisterRequest {

    @NotNull(message = "Admin ID is required")
    @Positive(message = "Admin ID must be positive")
    private Long adminId;

    @NotBlank(message = "Employee name is required")
    private String employeeName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must contain at least 8 characters")
    private String password;

    @NotBlank(message = "Job designation is required")
    private String role;

    private String contact;
}