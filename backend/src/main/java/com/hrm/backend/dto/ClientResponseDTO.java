package com.hrm.backend.dto;

import com.hrm.backend.entity.Client;

import java.math.BigDecimal;

/**
 * Client representation returned to administrators.
 *
 * Replaces returning the JPA entity directly: the entity exposes its owning Admin, which
 * exposes reviewed leaves, whose approvedBy is the same Admin again — Spring then fails
 * serialising the cycle with "Document nesting depth exceeds the maximum allowed".
 * A flat DTO removes the cycle and keeps the payload small.
 */
public record ClientResponseDTO(
        Long clientId,
        String clientCode,
        String vendor,
        String bdmName,
        String clientName,
        String position,
        String contactNumber,
        String hrName,
        Integer numberOfPositions,
        BigDecimal salaryOffered,
        String compensationType,
        BigDecimal compensationValue,
        BigDecimal budgetPerCandidate,
        BigDecimal percentagePerCandidate,
        BigDecimal tax,
        String hrContactDetails,
        Integer profilesSubmitted,
        Integer interviewsConducted,
        Integer selectedCandidates,
        String joiningDetails,
        String status
) {
    public static ClientResponseDTO from(Client client) {
        return new ClientResponseDTO(
                client.getClientId(),
                client.getClientCode(),
                client.getVendor(),
                client.getBdmName(),
                client.getClientName(),
                client.getPosition(),
                client.getContactNumber(),
                client.getHrName(),
                client.getNumberOfPositions(),
                client.getSalaryOffered(),
                client.getCompensationType(),
                client.getCompensationValue(),
                client.getBudgetPerCandidate(),
                client.getPercentagePerCandidate(),
                client.getTax(),
                client.getHrContactDetails(),
                client.getProfilesSubmitted(),
                client.getInterviewsConducted(),
                client.getSelectedCandidates(),
                client.getJoiningDetails(),
                client.getStatus()
        );
    }
}
