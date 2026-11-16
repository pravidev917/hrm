
package com.hrm.backend.controller;

import com.hrm.backend.dto.FeedbackRequestDTO;
import jakarta.validation.Valid;
import com.hrm.backend.dto.FeedbackResponseDTO;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.FeedbackService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employee/feedback")
public class EmployeeFeedbackController {

    private final FeedbackService feedbackService;
    private final EmployeeService employeeService;

    public EmployeeFeedbackController(
            FeedbackService feedbackService,
            EmployeeService employeeService) {

        this.feedbackService = feedbackService;
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<List<FeedbackResponseDTO>> getFeedback(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                feedbackService.getEmployeeFeedback(employeeId)
        );
    }

    @PostMapping
    public ResponseEntity<FeedbackResponseDTO> createFeedback(
            @Valid @RequestBody FeedbackRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                feedbackService.createForEmployee(employeeId, request)
        );
    }

    @PutMapping("/{feedbackId}")
    public ResponseEntity<FeedbackResponseDTO> updateFeedback(
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackRequestDTO request,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                feedbackService.updateForEmployee(
                        feedbackId,
                        employeeId,
                        request
                )
        );
    }

    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Void> deleteFeedback(
            @PathVariable Long feedbackId,
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName()
        );

        feedbackService.deleteForEmployee(feedbackId, employeeId);

        return ResponseEntity.noContent().build();
    }
}
