package com.hrm.backend.service;

import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository repository;

    public EmployeeService(EmployeeRepository repository) {
        this.repository = repository;
    }

    // Get all employees
    public List<Employee> getAllEmployees() {
        return repository.findAll();
    }

    // Get employees belonging to a specific admin
    public List<Employee> getEmployeesByAdmin(Long adminId) {
        return repository.findByAdminAdminId(adminId);
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

    // Update employee
    public Employee updateEmployee(
            Long employeeId,
            Long adminId,
            Employee employee) {

        Employee existing = getEmployeeByIdForAdmin(
                employeeId,
                adminId
        );

        if (employee.getEmployeeName() != null) {
            existing.setEmployeeName(employee.getEmployeeName());
        }

        if (employee.getRole() != null) {
            existing.setRole(employee.getRole());
        }

        if (employee.getContact() != null) {
            existing.setContact(employee.getContact());
        }

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
}