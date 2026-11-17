package com.hrm.backend.controller;

import com.hrm.backend.dto.*;
import com.hrm.backend.service.AuthService;
import com.hrm.backend.service.AuthService.LoginResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /** Cookie is HTTPS-only when true; must be true in production. */
    private final boolean cookieSecure;

    private final String cookieSameSite;

    /** Mirrors jwt.expiration so the cookie and the token expire together. */
    private final long cookieMaxAgeSeconds;

    public AuthController(
            AuthService authService,
            @Value("${app.cookie.secure:false}") boolean cookieSecure,
            @Value("${app.cookie.same-site:Lax}") String cookieSameSite,
            @Value("${jwt.expiration:86400000}") long cookieMaxAgeMillis) {

        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieMaxAgeSeconds = Math.max(0, cookieMaxAgeMillis / 1000);
    }

    // Admin login
    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponse> loginAdmin(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        LoginResult result = authService.loginAdmin(request, clientAddress(httpRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createAuthCookie(result.token()).toString())
                .body(result.response());
    }

    // Employee login: email, phone number or employee ID + password
    @PostMapping("/employee/login")
    public ResponseEntity<AuthResponse> loginEmployee(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        LoginResult result = authService.loginEmployee(request, clientAddress(httpRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createAuthCookie(result.token()).toString())
                .body(result.response());
    }

    // Logout: clear the cookie with the same attributes it was set with.
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createAuthCookie("", 0).toString())
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

    /**
     * The sign-in token lives only in an HTTP-only cookie: no header or body
     * copy is returned, so page scripts cannot read it.
     */
    private ResponseCookie createAuthCookie(String token) {
        return createAuthCookie(token, cookieMaxAgeSeconds);
    }

    private ResponseCookie createAuthCookie(String token, long maxAgeSeconds) {

        return ResponseCookie.from("HRM_AUTH", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite(cookieSameSite)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    /**
     * Client address used to throttle sign-in attempts. Behind the reverse proxy
     * this is the forwarded address (server.forward-headers-strategy=framework).
     */
    private String clientAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
