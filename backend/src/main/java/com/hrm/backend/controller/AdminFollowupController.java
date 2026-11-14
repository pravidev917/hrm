
package com.hrm.backend.controller;

import com.hrm.backend.dto.FollowupRequestDTO;
import com.hrm.backend.dto.FollowupResponseDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.FollowupService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/followups")
public class AdminFollowupController {

    private final FollowupService followupService;
    private final AdminService adminService;

    public AdminFollowupController(
            FollowupService followupService,
            AdminService adminService) {

        this.followupService = followupService;
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<List<FollowupResponseDTO>> getFollowups(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                followupService.getAdminFollowups(adminId)
        );
    }

    @PostMapping("/employees/{employeeId}")
    public ResponseEntity<FollowupResponseDTO> createFollowup(
            @PathVariable Long employeeId,
            @RequestBody FollowupRequestDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                followupService.createForAdmin(
                        employeeId,
                        adminId,
                        request
                )
        );
    }

    @PutMapping("/{followupId}")
    public ResponseEntity<FollowupResponseDTO> updateFollowup(
            @PathVariable Long followupId,
            @RequestBody FollowupRequestDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                followupService.updateForAdmin(
                        followupId,
                        adminId,
                        request
                )
        );
    }

    @DeleteMapping("/{followupId}")
    public ResponseEntity<Void> deleteFollowup(
            @PathVariable Long followupId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        followupService.deleteForAdmin(followupId, adminId);

        return ResponseEntity.noContent().build();
    }
}
