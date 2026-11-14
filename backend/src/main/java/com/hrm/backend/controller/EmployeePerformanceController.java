
package com.hrm.backend.controller;

import com.hrm.backend.dto.PerformanceRequestDTO;
import com.hrm.backend.dto.PerformanceResponseDTO;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.PerformanceService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employee/performance")
public class EmployeePerformanceController {

    private final PerformanceService performanceService;
    private final EmployeeService employeeService;

    public EmployeePerformanceController(
            PerformanceService performanceService,
            EmployeeService employeeService) {

        this.performanceService = performanceService;
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<List<PerformanceResponseDTO>> getPerformance(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                performanceService.getEmployeePerformance(employeeId)
        );
    }

    @PostMapping
    public ResponseEntity<PerformanceResponseDTO> createPerformance(
            @RequestBody PerformanceRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                performanceService.createForEmployee(employeeId, request)
        );
    }

    @PutMapping("/{performanceId}")
    public ResponseEntity<PerformanceResponseDTO> updatePerformance(
            @PathVariable Long performanceId,
            @RequestBody PerformanceRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                performanceService.updateForEmployee(
                        performanceId,
                        employeeId,
                        request
                )
        );
    }

    @DeleteMapping("/{performanceId}")
    public ResponseEntity<Void> deletePerformance(
            @PathVariable Long performanceId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        performanceService.deleteForEmployee(
                performanceId,
                employeeId
        );

        return ResponseEntity.noContent().build();
    }
}
