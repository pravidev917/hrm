package com.hrm.backend.dto;

/** Minimal client information an employee may see when filing a report. */
public record ClientOptionDTO(
        Long clientId,
        String clientName,
        String hrName,
        String status
) {
}
