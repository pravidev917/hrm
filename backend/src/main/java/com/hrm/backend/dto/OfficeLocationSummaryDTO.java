package com.hrm.backend.dto;

/**
 * Minimal office information for employees. The exact coordinates are not sent
 * to employees: they only need to know whether an office is configured and how
 * large the permitted area is.
 */
public record OfficeLocationSummaryDTO(
        boolean configured,
        String officeName,
        Integer radiusMeters,
        String timezone
) {
}
