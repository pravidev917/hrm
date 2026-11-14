
package com.hrm.backend.controller;

import com.hrm.backend.dto.PerformanceRequestDTO;
import com.hrm.backend.dto.PerformanceResponseDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.PerformanceService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/performance")
public class AdminPerformanceController {

    private final PerformanceService performanceService;
    private final AdminService adminService;

    public AdminPerformanceController(
            PerformanceService performanceService,
            AdminService adminService) {

        this.performanceService = performanceService;
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<List<PerformanceResponseDTO>> getPerformance(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                performanceService.getAdminPerformance(adminId)
        );
    }

    @PostMapping("/employees/{employeeId}")
    public ResponseEntity<PerformanceResponseDTO> createPerformance(
            @PathVariable Long employeeId,
            @RequestBody PerformanceRequestDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                performanceService.createForAdmin(
                        employeeId,
                        adminId,
                        request
                )
        );
    }

    @PutMapping("/{performanceId}")
    public ResponseEntity<PerformanceResponseDTO> updatePerformance(
            @PathVariable Long performanceId,
            @RequestBody PerformanceRequestDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                performanceService.updateForAdmin(
                        performanceId,
                        adminId,
                        request
                )
        );
    }

    @DeleteMapping("/{performanceId}")
    public ResponseEntity<Void> deletePerformance(
            @PathVariable Long performanceId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        performanceService.deleteForAdmin(
                performanceId,
                adminId
        );

        return ResponseEntity.noContent().build();
    }
}
