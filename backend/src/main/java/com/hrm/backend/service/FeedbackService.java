
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
    private final CompanyScopeService companyScope;
    private final CompanyTimeService companyTime;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            EmployeeRepository employeeRepository,
            CompanyScopeService companyScope,
            CompanyTimeService companyTime) {

        this.feedbackRepository = feedbackRepository;
        this.employeeRepository = employeeRepository;
        this.companyScope = companyScope;
        this.companyTime = companyTime;
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
                .findByEmployeeInCompanyOf(adminId)
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
        if (feedback.getSourcedDate() == null) {
            feedback.setSourcedDate(companyTime.todayForEmployee(employee));
        }

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
        if (feedback.getSourcedDate() == null) {
            feedback.setSourcedDate(companyTime.todayForEmployee(employee));
        }

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
                .findByIdInCompanyOf(
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
                .findByIdInCompanyOf(
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

        // Feedback is company data; another company's employee looks non-existent.
        if (!companyScope.manages(adminId, employee)) {

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

        // `feedback_received` predates this tracker and is NOT NULL in the database.
        // Mirror the pipeline status into it so the old column stays valid without
        // requiring existing rows to be rewritten.
        String status = clean(request.getStatus());
        String fallbackFeedback = status != null ? status : "SOURCED";

        // Keep the legacy text client column in sync with the new one.
        String clientName = clean(request.getClientName());
        if (clientName != null) feedback.setClient(clientName);
        // feedback_received is NOT NULL in the existing schema and predates this
        // tracker, so it always receives a value (new rows default to SOURCED).
        feedback.setFeedbackReceived(fallbackFeedback);

        // Every optional field is only written when the client actually sent it, so a
        // partial update (e.g. just marking a candidate JOINED) cannot erase the rest
        // of the record. Text fields can still be cleared by sending an empty string.
        feedback.setCandidateName(candidateName);
        feedback.setCandidateEmail(overwrite(request.getCandidateEmail(), feedback.getCandidateEmail()));
        feedback.setContactNumber(overwrite(request.getContactNumber(), feedback.getContactNumber()));
        feedback.setCurrentLocation(overwrite(request.getCurrentLocation(), feedback.getCurrentLocation()));
        feedback.setPreferredLocation(overwrite(request.getPreferredLocation(), feedback.getPreferredLocation()));
        feedback.setCurrentCompany(overwrite(request.getCurrentCompany(), feedback.getCurrentCompany()));
        feedback.setCurrentDesignation(overwrite(request.getCurrentDesignation(), feedback.getCurrentDesignation()));
        feedback.setHrName(overwrite(request.getHrName(), feedback.getHrName()));
        feedback.setClientName(overwrite(clientName, feedback.getClientName()));
        feedback.setComments(overwrite(request.getComments(), feedback.getComments()));
        feedback.setSourcingPlatform(overwrite(request.getSourcingPlatform(), feedback.getSourcingPlatform()));
        if (request.getSourcedDate() != null) feedback.setSourcedDate(request.getSourcedDate());

        if (request.getCurrentCtc() != null) feedback.setCurrentCtc(request.getCurrentCtc());
        if (request.getExpectedCtc() != null) feedback.setExpectedCtc(request.getExpectedCtc());
        if (request.getNoticePeriodDays() != null) feedback.setNoticePeriodDays(request.getNoticePeriodDays());
        if (request.getInterviewScheduled() != null) feedback.setInterviewScheduled(request.getInterviewScheduled());
        if (request.getInterviewScheduledDate() != null) feedback.setInterviewScheduledDate(request.getInterviewScheduledDate());
        if (request.getInterviewAttended() != null) feedback.setInterviewAttended(request.getInterviewAttended());
        if (request.getGoogleFeedbackGiven() != null) feedback.setGoogleFeedbackGiven(request.getGoogleFeedbackGiven());
        if (status != null) feedback.setStatus(status);
    }

    /**
     * Returns the new value when the client sent the field (even if blank, which
     * clears it), otherwise keeps what is already stored.
     */
    private String overwrite(String incoming, String current) {
        return incoming == null ? current : clean(incoming);
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
