package com.hrm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    /**
     * Sign-in identifier.
     * Administrators sign in with their email; employees sign in with their
     * email, phone number or employee ID.
     */
    @NotBlank(message = "Email, phone or employee ID is required")
    @Size(max = 150, message = "Email, phone or employee ID is too long")
    private String identifier;

    @NotBlank(message = "Password is required")
    @Size(max = 72, message = "Password is too long")
    private String password;
}