package com.hrm.backend.dto;

/**
 * Minimal client information an employee may see when filing a report.
 *
 * Read-only and deliberately narrow: employees pick a client, they never manage one.
 */
public record ClientOptionDTO(
        Long clientId,
        String clientCode,
        String clientName,
        String vendor,
        String position,
        String hrName,
        Integer numberOfPositions,
        String status
) {
}
