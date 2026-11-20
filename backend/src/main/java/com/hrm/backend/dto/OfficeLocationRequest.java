package com.hrm.backend.dto;

import jakarta.validation.constraints.*;

/**
 * Office settings as entered by an administrator.
 *
 * The company is never part of this payload: it is resolved from the
 * authenticated administrator, so settings cannot be written into another
 * company.
 */
public record OfficeLocationRequest(

        @NotBlank(message = "Office name is required")
        @Size(max = 150, message = "Office name cannot exceed 150 characters")
        String officeName,

        @NotNull(message = "Latitude is required")
        @Min(value = -90, message = "Latitude must be between -90 and 90")
        @Max(value = 90, message = "Latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @Min(value = -180, message = "Longitude must be between -180 and 180")
        @Max(value = 180, message = "Longitude must be between -180 and 180")
        Double longitude,

        @NotNull(message = "Allowed radius is required")
        @Min(value = 10, message = "Radius must be at least 10 metres")
        @Max(value = 10000, message = "Radius cannot exceed 10000 metres")
        Integer radiusMeters,

        @Size(max = 64, message = "Timezone cannot exceed 64 characters")
        String timezone
) {
}
