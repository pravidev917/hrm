package com.hrm.backend.dto;

import jakarta.validation.constraints.NotBlank;
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
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;
}