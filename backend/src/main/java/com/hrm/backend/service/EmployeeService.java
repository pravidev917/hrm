package com.hrm.backend.service;

import com.hrm.backend.dto.EmployeeUpdateRequest;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.EmployeeRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EmployeeService {

    private static final Set<String> SORTABLE =
            Set.of("employeeName", "email", "role", "accountStatus", "createdAt", "updatedAt");

    private final EmployeeRepository repository;
    private final AdminRepository adminRepository;

    public EmployeeService(EmployeeRepository repository, AdminRepository adminRepository) {
        this.repository = repository;
        this.adminRepository = adminRepository;
    }

    // Get all employees
    public List<Employee> getAllEmployees() {
        return repository.findAll();
    }

    // Get employees belonging to a specific admin
    public List<Employee> getEmployeesByAdmin(Long adminId) {
        return repository.findByAdminAdminId(adminId);
    }

    /**
     * Paged, filtered and sorted employee list scoped to one admin.
     * Used by the admin employees page (search box, status filter, column sorting).
     */
    @Transactional(readOnly = true)
    public Page<Employee> searchEmployeesByAdmin(
            Long adminId,
            String query,
            String status,
            int page,
            int size,
            String sortBy,
            String direction) {

        String property = SORTABLE.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(dir, property)
        );

        return repository.searchByAdmin(
                adminId,
                normalizeLike(query),
                normalizeStatus(status),
                pageable
        );
    }

    // Get employee by ID
    public Employee getEmployeeById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    // Get employee and verify ownership
    public Employee getEmployeeByIdForAdmin(Long employeeId, Long adminId) {

        Employee employee = getEmployeeById(employeeId);

        if (employee.getAdmin() == null ||
                employee.getAdmin().getAdminId() == null ||
                !employee.getAdmin().getAdminId().equals(adminId)) {

            throw new RuntimeException(
                    "Access denied: Employee does not belong to this admin"
            );
        }

        return employee;
    }

    // Create employee
    public Employee createEmployee(Employee employee) {
        return repository.save(employee);
    }

    /**
     * Update the editable profile fields of an employee.
     * Status and ownership are intentionally not editable here: they have their own
     * endpoints (approve/reject/activate/deactivate) and are set at registration.
     */
    @Transactional
    public Employee updateEmployee(
            Long employeeId,
            Long adminId,
            EmployeeUpdateRequest request) {

        Employee existing = getEmployeeByIdForAdmin(employeeId, adminId);

        existing.setEmployeeName(request.getEmployeeName().trim());
        existing.setRole(request.getRole().trim());

        String contact = request.getContact();
        existing.setContact(contact == null || contact.isBlank() ? null : contact.trim());

        String email = request.getEmail();
        if (email != null && !email.isBlank()) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals(existing.getEmail())) {
                boolean taken = repository.existsByEmail(normalized)
                        || adminRepository.existsByEmail(normalized);
                if (taken) {
                    throw new IllegalArgumentException("Email is already registered");
                }
                existing.setEmail(normalized);
            }
        }

        existing.setDateOfBirth(request.getDateOfBirth());
        existing.setJoiningDate(request.getJoiningDate());

        return repository.save(existing);
    }

    // Activate employee
    public Employee activateEmployee(
            Long employeeId,
            Long adminId) {

        Employee employee = getEmployeeByIdForAdmin(
                employeeId,
                adminId
        );

        employee.setAccountStatus("APPROVED");

        return repository.save(employee);
    }

    // Deactivate employee
    public Employee deactivateEmployee(
            Long employeeId,
            Long adminId) {

        Employee employee = getEmployeeByIdForAdmin(
                employeeId,
                adminId
        );

        employee.setAccountStatus("INACTIVE");

        return repository.save(employee);
    }

    // Delete employee
    public void deleteEmployee(Long id) {

        Employee employee = getEmployeeById(id);

        repository.delete(employee);
    }

    public Long getEmployeeIdByEmail(String email) {
        return repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Employee not found"))
                .getEmployeeId();
    }

    /** Lower-cased %term% for a LIKE query, or null when there is no search term. */
    private String normalizeLike(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }
}
