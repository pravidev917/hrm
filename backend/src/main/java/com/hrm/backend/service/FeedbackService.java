
package com.hrm.backend.service;

import com.hrm.backend.dto.FeedbackRequestDTO;
import com.hrm.backend.dto.FeedbackResponseDTO;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.Feedback;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.FeedbackRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final EmployeeRepository employeeRepository;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            EmployeeRepository employeeRepository) {

        this.feedbackRepository = feedbackRepository;
        this.employeeRepository = employeeRepository;
    }

    // EMPLOYEE: VIEW OWN FEEDBACK
    @Transactional(readOnly = true)
    public List<FeedbackResponseDTO> getEmployeeFeedback(Long employeeId) {

        return feedbackRepository
                .findByEmployee_EmployeeIdOrderByFeedbackIdDesc(employeeId)
                .stream()
                .map(FeedbackResponseDTO::from)
                .toList();
    }

    // ADMIN: VIEW FEEDBACK OF OWN EMPLOYEES
    @Transactional(readOnly = true)
    public List<FeedbackResponseDTO> getAdminFeedback(Long adminId) {

        return feedbackRepository
                .findByEmployee_Admin_AdminIdOrderByFeedbackIdDesc(adminId)
                .stream()
                .map(FeedbackResponseDTO::from)
                .toList();
    }

    // EMPLOYEE: CREATE OWN FEEDBACK
    @Transactional
    public FeedbackResponseDTO createForEmployee(
            Long employeeId,
            FeedbackRequestDTO request) {

        Employee employee = findEmployee(employeeId);

        Feedback feedback = new Feedback();
        feedback.setEmployee(employee);

        applyRequest(feedback, request);

        return FeedbackResponseDTO.from(
                feedbackRepository.save(feedback)
        );
    }

    // ADMIN: CREATE FEEDBACK FOR OWN EMPLOYEE
    @Transactional
    public FeedbackResponseDTO createForAdmin(
            Long employeeId,
            Long adminId,
            FeedbackRequestDTO request) {

        Employee employee = findEmployee(employeeId);

        verifyAdminOwnership(employee, adminId);

        Feedback feedback = new Feedback();
        feedback.setEmployee(employee);

        applyRequest(feedback, request);

        return FeedbackResponseDTO.from(
                feedbackRepository.save(feedback)
        );
    }

    // EMPLOYEE: UPDATE OWN FEEDBACK
    @Transactional
    public FeedbackResponseDTO updateForEmployee(
            Long feedbackId,
            Long employeeId,
            FeedbackRequestDTO request) {

        Feedback feedback = feedbackRepository
                .findByFeedbackIdAndEmployee_EmployeeId(
                        feedbackId,
                        employeeId
                )
                .orElseThrow(() -> notFound());

        applyRequest(feedback, request);

        return FeedbackResponseDTO.from(
                feedbackRepository.save(feedback)
        );
    }

    // ADMIN: UPDATE FEEDBACK OF OWN EMPLOYEE
    @Transactional
    public FeedbackResponseDTO updateForAdmin(
            Long feedbackId,
            Long adminId,
            FeedbackRequestDTO request) {

        Feedback feedback = feedbackRepository
                .findByFeedbackIdAndEmployee_Admin_AdminId(
                        feedbackId,
                        adminId
                )
                .orElseThrow(() -> notFound());

        applyRequest(feedback, request);

        return FeedbackResponseDTO.from(
                feedbackRepository.save(feedback)
        );
    }

    // EMPLOYEE: DELETE OWN FEEDBACK
    @Transactional
    public void deleteForEmployee(
            Long feedbackId,
            Long employeeId) {

        Feedback feedback = feedbackRepository
                .findByFeedbackIdAndEmployee_EmployeeId(
                        feedbackId,
                        employeeId
                )
                .orElseThrow(() -> notFound());

        feedbackRepository.delete(feedback);
    }

    // ADMIN: DELETE FEEDBACK OF OWN EMPLOYEE
    @Transactional
    public void deleteForAdmin(
            Long feedbackId,
            Long adminId) {

        Feedback feedback = feedbackRepository
                .findByFeedbackIdAndEmployee_Admin_AdminId(
                        feedbackId,
                        adminId
                )
                .orElseThrow(() -> notFound());

        feedbackRepository.delete(feedback);
    }

    // FIND EMPLOYEE
    private Employee findEmployee(Long employeeId) {

        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found"
                ));
    }

    // VERIFY ADMIN OWNERSHIP
    private void verifyAdminOwnership(
            Employee employee,
            Long adminId) {

        if (employee.getAdmin() == null ||
                !adminId.equals(employee.getAdmin().getAdminId())) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Employee not found"
            );
        }
    }

    // VALIDATE AND APPLY REQUEST
    private void applyRequest(
            Feedback feedback,
            FeedbackRequestDTO request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Feedback request is required"
            );
        }

        if (request.getCandidateName() == null ||
                request.getCandidateName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Candidate name is required"
            );
        }

        String candidateName = request.getCandidateName().trim();

        if (candidateName.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Candidate name cannot exceed 100 characters"
            );
        }

        if (request.getFeedbackReceived() == null ||
                request.getFeedbackReceived().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Feedback received is required"
            );
        }

        String feedbackReceived = request.getFeedbackReceived().trim();

        if (feedbackReceived.length() > 30) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Feedback received cannot exceed 30 characters"
            );
        }

        String client = clean(request.getClient());

        if (client != null && client.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client cannot exceed 150 characters"
            );
        }

        feedback.setCandidateName(candidateName);
        feedback.setClient(client);
        feedback.setFeedbackReceived(feedbackReceived);
        feedback.setComments(clean(request.getComments()));
    }

    // CLEAN STRING VALUES
    private String clean(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    // NOT FOUND RESPONSE
    private ResponseStatusException notFound() {

        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Feedback not found"
        );
    }
}
