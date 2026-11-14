
package com.hrm.backend.dto;

import com.hrm.backend.entity.Feedback;

public record FeedbackResponseDTO(
        Long feedbackId,
        Long employeeId,
        String employeeName,
        String client,
        String candidateName,
        String feedbackReceived,
        String comments
) {

    public static FeedbackResponseDTO from(Feedback feedback) {

        return new FeedbackResponseDTO(
                feedback.getFeedbackId(),
                feedback.getEmployee().getEmployeeId(),
                feedback.getEmployee().getEmployeeName(),
                feedback.getClient(),
                feedback.getCandidateName(),
                feedback.getFeedbackReceived(),
                feedback.getComments()
        );
    }
}
