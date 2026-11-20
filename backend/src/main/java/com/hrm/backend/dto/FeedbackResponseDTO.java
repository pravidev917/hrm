package com.hrm.backend.dto;

import com.hrm.backend.entity.Feedback;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A sourced candidate as returned to the UI. */
public record FeedbackResponseDTO(
        Long feedbackId,
        Long employeeId,
        String employeeName,
        String candidateName,
        String candidateEmail,
        String contactNumber,
        String currentLocation,
        String preferredLocation,
        BigDecimal currentCtc,
        BigDecimal expectedCtc,
        String currentCompany,
        String currentDesignation,
        Integer noticePeriodDays,
        String hrName,
        String clientName,
        Boolean interviewScheduled,
        LocalDate interviewScheduledDate,
        Boolean interviewAttended,
        String status,
        Boolean googleFeedbackGiven,
        String comments,
        String sourcingPlatform,
        LocalDate sourcedDate
) {

    public static FeedbackResponseDTO from(Feedback f) {
        return new FeedbackResponseDTO(
                f.getFeedbackId(),
                f.getEmployee().getEmployeeId(),
                f.getEmployee().getEmployeeName(),
                f.getCandidateName(),
                f.getCandidateEmail(),
                f.getContactNumber(),
                f.getCurrentLocation(),
                f.getPreferredLocation(),
                f.getCurrentCtc(),
                f.getExpectedCtc(),
                f.getCurrentCompany(),
                f.getCurrentDesignation(),
                f.getNoticePeriodDays(),
                f.getHrName(),
                // legacy rows stored the client in `client`; prefer the new column
                f.getClientName() != null ? f.getClientName() : f.getClient(),
                f.getInterviewScheduled(),
                f.getInterviewScheduledDate(),
                f.getInterviewAttended(),
                // legacy rows have no status; surface the old free-text value instead of null
                f.getStatus() != null ? f.getStatus() : f.getFeedbackReceived(),
                f.getGoogleFeedbackGiven(),
                f.getComments(),
                f.getSourcingPlatform(),
                f.getSourcedDate()
        );
    }
}
