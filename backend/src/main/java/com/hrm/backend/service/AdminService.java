package com.hrm.backend.service;

import com.hrm.backend.dto.AdminCreateRequest;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class AdminService {

    private final AdminRepository repository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(
            AdminRepository repository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder) {

        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Admin> getAllAdmins() {
        return repository.findAll();
    }

    public Admin getAdminById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
    }

    // Get admin ID using logged-in admin email
    public Long getAdminIdByEmail(String email) {

        return repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"))
                .getAdminId();
    }

    public Admin createAdmin(Admin admin) {
        return repository.save(admin);
    }

    /**
     * An administrator creates another administrator account.
     * Emails are shared with employees (both are sign-in identifiers), so the
     * uniqueness check covers both tables.
     */
    @Transactional
    public Admin createAdminAccount(AdminCreateRequest request) {

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (repository.existsByEmail(email) || employeeRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already used by another account"
            );
        }

        Admin admin = new Admin();
        admin.setAdminName(request.getAdminName().trim());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setRoleAccessLevel("ADMIN");
        admin.setContactDetails(
                request.getContactDetails() == null || request.getContactDetails().isBlank()
                        ? null
                        : request.getContactDetails().trim()
        );
        admin.setStatus("ACTIVE");

        return repository.save(admin);
    }

    public Admin updateAdmin(Long id, Admin admin) {
        Admin existing = getAdminById(id);

        existing.setAdminName(admin.getAdminName());
        existing.setEmail(admin.getEmail());
        existing.setRoleAccessLevel(admin.getRoleAccessLevel());
        existing.setContactDetails(admin.getContactDetails());
        existing.setStatus(admin.getStatus());

        return repository.save(existing);
    }

    public void deleteAdmin(Long id) {
        repository.delete(getAdminById(id));
    }
}
