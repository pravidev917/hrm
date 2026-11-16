
package com.hrm.backend.controller;

import com.hrm.backend.dto.FeedbackRequestDTO;
import jakarta.validation.Valid;
import com.hrm.backend.dto.FeedbackResponseDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.FeedbackService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;
    private final AdminService adminService;

    public AdminFeedbackController(
            FeedbackService feedbackService,
            AdminService adminService) {

        this.feedbackService = feedbackService;
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<List<FeedbackResponseDTO>> getFeedback(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                feedbackService.getAdminFeedback(adminId)
        );
    }

    @PostMapping("/employees/{employeeId}")
    public ResponseEntity<FeedbackResponseDTO> createFeedback(
            @PathVariable Long employeeId,
            @Valid @RequestBody FeedbackRequestDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                feedbackService.createForAdmin(
                        employeeId,
                        adminId,
                        request
                )
        );
    }

    @PutMapping("/{feedbackId}")
    public ResponseEntity<FeedbackResponseDTO> updateFeedback(
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackRequestDTO request,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                feedbackService.updateForAdmin(
                        feedbackId,
                        adminId,
                        request
                )
        );
    }

    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Void> deleteFeedback(
            @PathVariable Long feedbackId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        feedbackService.deleteForAdmin(feedbackId, adminId);

        return ResponseEntity.noContent().build();
    }
}
