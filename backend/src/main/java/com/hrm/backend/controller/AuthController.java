
package com.hrm.backend.controller;

import com.hrm.backend.dto.*;
import com.hrm.backend.service.AuthService;
import com.hrm.backend.service.AuthService.LoginResult;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Admin registration
    @PostMapping("/admin/register")
    public ResponseEntity<AuthResponse> registerAdmin(
            @Valid @RequestBody AdminRegisterRequest request) {

        AuthResponse response = authService.registerAdmin(request);

        return ResponseEntity.ok(response);
    }

    // Admin login
    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponse> loginAdmin(
            @Valid @RequestBody LoginRequest request) {

        LoginResult result = authService.loginAdmin(request);

        ResponseCookie cookie = createAuthCookie(result.token());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    // Employee registration
    @PostMapping("/employee/register")
    public ResponseEntity<AuthResponse> registerEmployee(
            @Valid @RequestBody EmployeeRegisterRequest request) {

        AuthResponse response = authService.registerEmployee(request);

        return ResponseEntity.ok(response);
    }

    // Employee login
    @PostMapping("/employee/login")
    public ResponseEntity<AuthResponse> loginEmployee(
            @Valid @RequestBody LoginRequest request) {

        LoginResult result = authService.loginEmployee(request);

        ResponseCookie cookie = createAuthCookie(result.token());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header("X-Auth-Token", result.token())
                .body(result.response());
    }

    // Logout
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {

        ResponseCookie cookie = ResponseCookie.from("HRM_AUTH", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("message", "Logged out successfully"));
    }

    // Current authenticated user
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> currentUser(
            Authentication authentication) {

        return ResponseEntity.ok(
                Map.of(
                        "email", authentication.getName(),
                        "authorities", authentication.getAuthorities()
                )
        );
    }

    // Create authentication cookie
    private ResponseCookie createAuthCookie(String token) {

        return ResponseCookie.from("HRM_AUTH", token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(86400)
                .build();
    }
}
