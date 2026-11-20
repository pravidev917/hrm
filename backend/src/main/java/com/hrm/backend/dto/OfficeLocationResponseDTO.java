package com.hrm.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Office settings returned to administrators of the company.
 * Includes the last editor for the audit trail shown in the settings page.
 */
public record OfficeLocationResponseDTO(
        Long officeLocationId,
        String officeName,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer radiusMeters,
        String timezone,
        Long updatedByAdminId,
        String updatedByName,
        LocalDateTime updatedAt
) {
}
