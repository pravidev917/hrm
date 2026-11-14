package com.hrm.backend.service;

import com.hrm.backend.entity.Admin;
import com.hrm.backend.repository.AdminRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private final AdminRepository repository;

    public AdminService(AdminRepository repository) {
        this.repository = repository;
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