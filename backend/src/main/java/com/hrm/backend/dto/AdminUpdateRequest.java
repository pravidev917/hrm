package com.hrm.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload used by an administrator to edit another administrator account.
 *
 * The password is optional: leaving it empty keeps the current one. A supplied
 * password is hashed before it is stored and never returned by any endpoint.
 */
@Getter
@Setter
public class AdminUpdateRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String adminName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    @Size(max = 50, message = "Phone cannot exceed 50 characters")
    private String contactDetails;

    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String password;
}
