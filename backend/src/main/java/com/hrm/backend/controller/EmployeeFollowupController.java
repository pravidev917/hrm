
package com.hrm.backend.controller;

import com.hrm.backend.dto.FollowupRequestDTO;
import com.hrm.backend.dto.FollowupResponseDTO;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.FollowupService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employee/followups")
public class EmployeeFollowupController {

    private final FollowupService followupService;
    private final EmployeeService employeeService;

    public EmployeeFollowupController(
            FollowupService followupService,
            EmployeeService employeeService) {

        this.followupService = followupService;
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<List<FollowupResponseDTO>> getFollowups(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                followupService.getEmployeeFollowups(employeeId)
        );
    }

    @PostMapping
    public ResponseEntity<FollowupResponseDTO> createFollowup(
            @RequestBody FollowupRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                followupService.createForEmployee(employeeId, request)
        );
    }

    @PutMapping("/{followupId}")
    public ResponseEntity<FollowupResponseDTO> updateFollowup(
            @PathVariable Long followupId,
            @RequestBody FollowupRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                followupService.updateForEmployee(
                        followupId,
                        employeeId,
                        request
                )
        );
    }

    @DeleteMapping("/{followupId}")
    public ResponseEntity<Void> deleteFollowup(
            @PathVariable Long followupId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        followupService.deleteForEmployee(followupId, employeeId);

        return ResponseEntity.noContent().build();
    }
}
