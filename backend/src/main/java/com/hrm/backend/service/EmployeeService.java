package com.hrm.backend.service;

import com.hrm.backend.dto.EmployeeCreateRequest;
import com.hrm.backend.dto.EmployeeUpdateRequest;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.FeedbackRepository;
import com.hrm.backend.repository.FollowupRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.MonthlyPerformanceRepository;
import com.hrm.backend.repository.PerformanceRepository;
import com.hrm.backend.repository.WeeklyReportRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EmployeeService {

    private static final Set<String> SORTABLE =
            Set.of("employeeName", "email", "role", "accountStatus", "createdAt", "updatedAt",
                    "employeeCode", "department");

    private final EmployeeRepository repository;
    private final AdminRepository adminRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveManagementRepository leaveRepository;
    private final DailyReportRepository dailyReportRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final PerformanceRepository performanceRepository;
    private final FollowupRepository followupRepository;
    private final FeedbackRepository feedbackRepository;
    private final MonthlyPerformanceRepository monthlyPerformanceRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository repository,
            AdminRepository adminRepository,
            AttendanceRepository attendanceRepository,
            LeaveManagementRepository leaveRepository,
            DailyReportRepository dailyReportRepository,
            WeeklyReportRepository weeklyReportRepository,
            PerformanceRepository performanceRepository,
            FollowupRepository followupRepository,
            FeedbackRepository feedbackRepository,
            MonthlyPerformanceRepository monthlyPerformanceRepository,
            PasswordEncoder passwordEncoder) {

        this.repository = repository;
        this.adminRepository = adminRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRepository = leaveRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.weeklyReportRepository = weeklyReportRepository;
        this.performanceRepository = performanceRepository;
        this.followupRepository = followupRepository;
        this.feedbackRepository = feedbackRepository;
        this.monthlyPerformanceRepository = monthlyPerformanceRepository;
        this.passwordEncoder = passwordEncoder;
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
     * Administrator creates an employee account.
     *
     * The account is usable immediately (status APPROVED): the system no longer
     * has self-registration, so there is nothing to approve afterwards.
     * Email, phone and employee ID are all sign-in identifiers and must be unique.
     */
    @Transactional
    public Employee createEmployeeForAdmin(Long adminId, EmployeeCreateRequest request) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Admin not found"));

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String employeeCode = request.getEmployeeCode().trim();
        String contact = request.getContact().trim();

        if (repository.existsByEmail(email) || adminRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email is already used by another account");
        }
        if (repository.existsByEmployeeCodeIgnoreCase(employeeCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Employee ID is already used by another employee");
        }
        if (repository.existsByContact(contact)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Phone number is already used by another employee");
        }

        Employee employee = new Employee();
        employee.setAdmin(admin);
        employee.setEmployeeName(request.getEmployeeName().trim());
        employee.setEmployeeCode(employeeCode);
        employee.setEmail(email);
        employee.setContact(contact);
        employee.setDepartment(trimToNull(request.getDepartment()));
        employee.setSalary(request.getSalary());
        employee.setRole(request.getRole().trim());
        employee.setPassword(passwordEncoder.encode(request.getPassword()));
        employee.setAccountStatus("APPROVED");

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
        existing.setContact(nullableTrim(request.getContact()));
        existing.setDepartment(nullableTrim(request.getDepartment()));
        existing.setSalary(request.getSalary());

        String email = request.getEmail();
        if (email != null && !email.isBlank()) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals(existing.getEmail())) {
                boolean taken = repository.existsByEmail(normalized)
                        || adminRepository.existsByEmail(normalized);
                if (taken) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Email is already used by another account");
                }
                existing.setEmail(normalized);
            }
        }

        String employeeCode = request.getEmployeeCode();
        if (employeeCode != null && !employeeCode.isBlank()) {
            String trimmedCode = employeeCode.trim();
            boolean changed = existing.getEmployeeCode() == null
                    || !existing.getEmployeeCode().equalsIgnoreCase(trimmedCode);
            if (changed && repository.existsByEmployeeCodeIgnoreCase(trimmedCode)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Employee ID is already used by another employee");
            }
            existing.setEmployeeCode(trimmedCode);
        }

        // Phone doubles as a sign-in identifier, so a changed number must be free.
        String contact = request.getContact();
        if (contact != null && !contact.isBlank()) {
            String trimmedContact = contact.trim();
            boolean changed = existing.getContact() == null
                    || !existing.getContact().equals(trimmedContact);
            if (changed && repository.existsByContact(trimmedContact)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Phone number is already used by another employee");
            }
        }

        // Optional password reset: only replaced when the administrator typed one.
        String password = request.getPassword();
        if (password != null && !password.isBlank()) {
            existing.setPassword(passwordEncoder.encode(password));
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

    /**
     * Administrator deletes an employee and every record owned by them.
     * Children are removed explicitly because the entities do not declare
     * database-level cascades.
     */
    @Transactional
    public void deleteEmployeeForAdmin(Long employeeId, Long adminId) {

        Employee employee = getEmployeeByIdForAdmin(employeeId, adminId);

        attendanceRepository.deleteByEmployeeEmployeeId(employeeId);
        leaveRepository.deleteByEmployeeEmployeeId(employeeId);
        dailyReportRepository.deleteByEmployeeEmployeeId(employeeId);
        weeklyReportRepository.deleteByEmployeeEmployeeId(employeeId);
        performanceRepository.deleteByEmployee_EmployeeId(employeeId);
        followupRepository.deleteByEmployee_EmployeeId(employeeId);
        feedbackRepository.deleteByEmployee_EmployeeId(employeeId);
        monthlyPerformanceRepository.deleteByEmployeeId(employeeId);

        repository.delete(employee);
    }

    /** Trimmed value or null when blank. */
    private String nullableTrim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return nullableTrim(value);
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
