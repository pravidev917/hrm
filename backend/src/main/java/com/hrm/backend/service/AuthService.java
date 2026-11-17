package com.hrm.backend.service;

import com.hrm.backend.dto.AuthResponse;
import com.hrm.backend.dto.LoginRequest;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.security.JwtService;
import com.hrm.backend.security.LoginAttemptService;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    private final AdminRepository adminRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(
            AdminRepository adminRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptService loginAttemptService) {

        this.adminRepository = adminRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    // ADMIN LOGIN (email + password)

    @Transactional(readOnly = true)
    public LoginResult loginAdmin(LoginRequest request, String clientAddress) {

        String email = request.getIdentifier().trim().toLowerCase(Locale.ROOT);

        loginAttemptService.assertNotLocked(email, clientAddress);

        Optional<Admin> found = adminRepository.findByEmail(email);

        if (found.isEmpty()
                || !passwordEncoder.matches(request.getPassword(), found.get().getPassword())) {

            loginAttemptService.recordFailure(email, clientAddress);

            throw invalidCredentials();
        }

        Admin admin = found.get();

        if (!"ACTIVE".equals(admin.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin account is inactive"
            );
        }

        loginAttemptService.recordSuccess(email, clientAddress);

        String token = jwtService.generateToken(
                admin.getEmail(),
                "ADMIN",
                admin.getAdminId()
        );

        AuthResponse response = new AuthResponse(
                "Admin login successful",
                admin.getAdminId(),
                admin.getAdminName(),
                admin.getEmail(),
                "ADMIN",
                admin.getStatus()
        );

        return new LoginResult(token, response);
    }

    // EMPLOYEE LOGIN (email, phone number or employee ID + password)

    @Transactional(readOnly = true)
    public LoginResult loginEmployee(LoginRequest request, String clientAddress) {

        String identifier = request.getIdentifier().trim().toLowerCase(Locale.ROOT);

        loginAttemptService.assertNotLocked(identifier, clientAddress);

        Optional<Employee> found = findByLoginIdentifier(request.getIdentifier());

        if (found.isEmpty()
                || !passwordEncoder.matches(request.getPassword(), found.get().getPassword())) {

            loginAttemptService.recordFailure(identifier, clientAddress);

            throw invalidCredentials();
        }

        Employee employee = found.get();

        if (!"APPROVED".equals(employee.getAccountStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Employee account is inactive. Contact your administrator."
            );
        }

        loginAttemptService.recordSuccess(identifier, clientAddress);

        String token = jwtService.generateToken(
                employee.getEmail(),
                "EMPLOYEE",
                employee.getEmployeeId()
        );

        AuthResponse response = new AuthResponse(
                "Employee login successful",
                employee.getEmployeeId(),
                employee.getEmployeeName(),
                employee.getEmail(),
                "EMPLOYEE",
                employee.getAccountStatus()
        );

        return new LoginResult(token, response);
    }

    /**
     * Resolve an employee by any of the accepted sign-in identifiers:
     * email (case-insensitive), employee ID (case-insensitive) or phone number.
     */
    private Optional<Employee> findByLoginIdentifier(String rawIdentifier) {

        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            return Optional.empty();
        }

        String identifier = rawIdentifier.trim();

        Optional<Employee> byEmail = employeeRepository.findByEmail(
                identifier.toLowerCase(Locale.ROOT));
        if (byEmail.isPresent()) {
            return byEmail;
        }

        Optional<Employee> byCode = employeeRepository
                .findByEmployeeCodeIgnoreCase(identifier);
        if (byCode.isPresent()) {
            return byCode;
        }

        // Phone numbers: try the exact value first, then a digits-only match so
        // "+91 98765 43210" and "9876543210" reach the same account.
        Optional<Employee> byPhone = employeeRepository.findByContact(identifier);
        if (byPhone.isPresent()) {
            return byPhone;
        }

        String digits = identifier.replaceAll("[^0-9]", "");
        if (digits.length() >= 7 && !digits.equals(identifier)) {
            return employeeRepository.findByContact(digits);
        }

        return Optional.empty();
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email, phone, employee ID or password"
        );
    }

    // Login result: token is used for the cookie, response is returned to frontend
    public record LoginResult(String token, AuthResponse response) {
    }
}
