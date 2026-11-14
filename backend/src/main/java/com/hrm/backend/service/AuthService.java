
package com.hrm.backend.service;

import com.hrm.backend.dto.*;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.security.JwtService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    @Value("${hrm.registration.employee-code:}")
    private String employeeRegistrationCode;
    private final AdminRepository adminRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AdminRepository adminRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.adminRepository = adminRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // ADMIN REGISTRATION
    @Transactional
    public AuthResponse registerAdmin(AdminRegisterRequest request) {
        if (adminRepository.count() > 0) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Admin registration is closed. Contact an existing administrator."
            );
        }
        String email = request.getEmail().trim().toLowerCase();

        if (adminRepository.existsByEmail(email)
                || employeeRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        Admin admin = new Admin();

        admin.setAdminName(request.getAdminName().trim());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setRoleAccessLevel("ADMIN");
        admin.setContactDetails(request.getContactDetails());
        admin.setStatus("ACTIVE");

        Admin savedAdmin = adminRepository.save(admin);

        return new AuthResponse(
                "Admin registered successfully",
                savedAdmin.getAdminId(),
                savedAdmin.getAdminName(),
                savedAdmin.getEmail(),
                "ADMIN",
                savedAdmin.getStatus()
        );
    }

    // EMPLOYEE REGISTRATION
    @Transactional
    public AuthResponse registerEmployee(EmployeeRegisterRequest request) {
        if (employeeRegistrationCode.isBlank()
                || !employeeRegistrationCode.equals(request.getRegistrationCode())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Invalid employee registration code"
            );
        }
        String email = request.getEmail().trim().toLowerCase();

        if (adminRepository.existsByEmail(email)
                || employeeRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        Admin admin = adminRepository.findById(request.getAdminId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin not found"
                ));

        if (!"ACTIVE".equals(admin.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin account is inactive"
            );
        }

        Employee employee = new Employee();

        employee.setAdmin(admin);
        employee.setEmployeeName(request.getEmployeeName().trim());
        employee.setEmail(email);
        employee.setPassword(passwordEncoder.encode(request.getPassword()));
        employee.setRole(request.getRole().trim());
        employee.setContact(request.getContact());
        employee.setAccountStatus("PENDING");

        Employee savedEmployee = employeeRepository.save(employee);

        return new AuthResponse(
                "Employee registered. Awaiting admin approval.",
                savedEmployee.getEmployeeId(),
                savedEmployee.getEmployeeName(),
                savedEmployee.getEmail(),
                "EMPLOYEE",
                savedEmployee.getAccountStatus()
        );
    }

    // ADMIN LOGIN
    @Transactional(readOnly = true)
    public LoginResult loginAdmin(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        if (!"ACTIVE".equals(admin.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin account is inactive"
            );
        }

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

    // EMPLOYEE LOGIN
    @Transactional(readOnly = true)
    public LoginResult loginEmployee(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        if (!passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        if (!"APPROVED".equals(employee.getAccountStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Employee account is not approved"
            );
        }

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

    // Login result: token is used for the cookie, response is returned to frontend
    public record LoginResult(String token, AuthResponse response) {
    }

   
    // APPROVE EMPLOYEE
    @Transactional
    public AuthResponse approveEmployee(Long employeeId, String adminEmail) {

        Admin admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin not found"
                ));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found"
                ));

        // Verify employee belongs to this admin
        if (!employee.getAdmin().getAdminId().equals(admin.getAdminId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot manage employees belonging to another admin"
            );
        }

        if (!"PENDING".equals(employee.getAccountStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee is not awaiting approval"
            );
        }

        employee.setAccountStatus("APPROVED");

        Employee savedEmployee = employeeRepository.save(employee);

        return new AuthResponse(
                "Employee approved successfully",
                savedEmployee.getEmployeeId(),
                savedEmployee.getEmployeeName(),
                savedEmployee.getEmail(),
                "EMPLOYEE",
                savedEmployee.getAccountStatus()
        );
    }

    // REJECT EMPLOYEE
    @Transactional
    public AuthResponse rejectEmployee(Long employeeId, String adminEmail) {

        Admin admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin not found"
                ));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found"
                ));

        // Verify employee belongs to this admin
        if (!employee.getAdmin().getAdminId().equals(admin.getAdminId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot manage employees belonging to another admin"
            );
        }

        if (!"PENDING".equals(employee.getAccountStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee is not awaiting approval"
            );
        }

        employee.setAccountStatus("REJECTED");

        Employee savedEmployee = employeeRepository.save(employee);

        return new AuthResponse(
                "Employee rejected successfully",
                savedEmployee.getEmployeeId(),
                savedEmployee.getEmployeeName(),
                savedEmployee.getEmail(),
                "EMPLOYEE",
                savedEmployee.getAccountStatus()
        );
    }

}
