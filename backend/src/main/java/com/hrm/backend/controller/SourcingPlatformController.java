package com.hrm.backend.controller;

import com.hrm.backend.dto.SourcingPlatformDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.SourcingPlatformService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Company-managed sourcing platform options. */
@RestController
@RequestMapping("/api")
public class SourcingPlatformController {

    private final SourcingPlatformService platformService;
    private final AdminService adminService;
    private final EmployeeService employeeService;

    public SourcingPlatformController(
            SourcingPlatformService platformService,
            AdminService adminService,
            EmployeeService employeeService) {
        this.platformService = platformService;
        this.adminService = adminService;
        this.employeeService = employeeService;
    }

    @GetMapping("/admin/sourcing-platforms")
    public ResponseEntity<List<SourcingPlatformDTO>> listForAdmin(Authentication authentication) {
        Long adminId = adminService.getAdminIdByEmail(authentication.getName());
        return ResponseEntity.ok(platformService.listForAdmin(adminId));
    }

    @PostMapping("/admin/sourcing-platforms")
    public ResponseEntity<SourcingPlatformDTO> create(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        Boolean active = body.get("active") == null ? null : Boolean.valueOf(String.valueOf(body.get("active")));
        Integer sortOrder = body.get("sortOrder") == null ? null : Integer.valueOf(String.valueOf(body.get("sortOrder")));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformService.create(adminId, name, active, sortOrder));
    }

    @PutMapping("/admin/sourcing-platforms/{id}")
    public ResponseEntity<SourcingPlatformDTO> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(authentication.getName());
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        Boolean active = body.get("active") == null ? null : Boolean.valueOf(String.valueOf(body.get("active")));
        Integer sortOrder = body.get("sortOrder") == null ? null : Integer.valueOf(String.valueOf(body.get("sortOrder")));

        return ResponseEntity.ok(platformService.update(adminId, id, name, active, sortOrder));
    }

    @GetMapping("/employee/sourcing-platforms")
    public ResponseEntity<List<SourcingPlatformDTO>> listForEmployee(Authentication authentication) {
        Long employeeId = employeeService.getEmployeeIdByEmail(authentication.getName());
        Long adminId = employeeService.getEmployeeById(employeeId).getAdmin().getAdminId();
        return ResponseEntity.ok(platformService.listActiveForEmployee(employeeId, adminId));
    }
}
