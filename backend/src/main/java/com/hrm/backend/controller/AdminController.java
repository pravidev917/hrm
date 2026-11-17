
package com.hrm.backend.controller;
import com.hrm.backend.dto.AdminCreateRequest;
import com.hrm.backend.dto.AdminResponseDTO;
import com.hrm.backend.dto.EmployeeCreateRequest;
import com.hrm.backend.dto.EmployeeResponseDTO;
import com.hrm.backend.dto.EmployeeUpdateRequest;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.EmployeeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final EmployeeService employeeService;
    private final AdminService adminService;

    public AdminController(
            EmployeeService employeeService,
            AdminService adminService) {

        this.employeeService = employeeService;
        this.adminService = adminService;
    }

    // ADMINISTRATORS

    @GetMapping("/admins")
    public ResponseEntity<List<AdminResponseDTO>> getAdmins() {

        return ResponseEntity.ok(
                adminService.getAllAdmins()
                        .stream()
                        .map(AdminResponseDTO::from)
                        .toList()
        );
    }

    @PostMapping("/admins")
    public ResponseEntity<AdminResponseDTO> createAdmin(
            @Valid @RequestBody AdminCreateRequest request) {

        Admin created = adminService.createAdminAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminResponseDTO.from(created));
    }

    // CREATE EMPLOYEE

    @PostMapping("/employees")
    public ResponseEntity<EmployeeResponseDTO> createEmployee(
            @Valid @RequestBody EmployeeCreateRequest request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        Employee created = employeeService.createEmployeeForAdmin(
                adminId,
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EmployeeResponseDTO.from(created));
    }

    // EMPLOYEE LISTING

    @GetMapping("/employees")
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployees(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        List<EmployeeResponseDTO> employees =
                employeeService.getEmployeesByAdmin(adminId)
                        .stream()
                        .map(EmployeeResponseDTO::from)
                        .toList();

        return ResponseEntity.ok(employees);
    }

    /**
     * Paged / searched / sorted employee list.
     * Returns a Spring Page envelope: { content, page, size, totalElements, totalPages }.
     */
    @GetMapping("/employees/page")
    public ResponseEntity<Page<EmployeeResponseDTO>> getEmployeesPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        Page<EmployeeResponseDTO> result = employeeService
                .searchEmployeesByAdmin(adminId, search, status, page, size, sortBy, direction)
                .map(EmployeeResponseDTO::from);

        return ResponseEntity.ok(result);
    }

    // EMPLOYEE PROFILE

    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<EmployeeResponseDTO> getEmployee(
            @PathVariable Long employeeId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        Employee employee = employeeService.getEmployeeByIdForAdmin(
                employeeId,
                adminId
        );

        return ResponseEntity.ok(
                EmployeeResponseDTO.from(employee)
        );
    }

    // EDIT EMPLOYEE

    @PutMapping("/employees/{employeeId}")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeUpdateRequest request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        Employee updated = employeeService.updateEmployee(
                employeeId,
                adminId,
                request
        );

        return ResponseEntity.ok(
                EmployeeResponseDTO.from(updated)
        );
    }

    // ACTIVATE EMPLOYEE

    @PatchMapping("/employees/{employeeId}/activate")
    public ResponseEntity<EmployeeResponseDTO> activateEmployee(
            @PathVariable Long employeeId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        Employee employee = employeeService.activateEmployee(
                employeeId,
                adminId
        );

        return ResponseEntity.ok(
                EmployeeResponseDTO.from(employee)
        );
    }

    // DEACTIVATE EMPLOYEE

    @PatchMapping("/employees/{employeeId}/deactivate")
    public ResponseEntity<EmployeeResponseDTO> deactivateEmployee(
            @PathVariable Long employeeId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        Employee employee = employeeService.deactivateEmployee(
                employeeId,
                adminId
        );

        return ResponseEntity.ok(
                EmployeeResponseDTO.from(employee)
        );
    }

    // DELETE EMPLOYEE

    @DeleteMapping("/employees/{employeeId}")
    public ResponseEntity<Map<String, String>> deleteEmployee(
            @PathVariable Long employeeId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        employeeService.deleteEmployeeForAdmin(employeeId, adminId);

        return ResponseEntity.ok(
                Map.of("message", "Employee deleted")
        );
    }
}
