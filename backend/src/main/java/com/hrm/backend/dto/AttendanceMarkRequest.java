package com.hrm.backend.dto;

import jakarta.validation.constraints.*;

/**
 * A location fix captured by the browser for an office attendance submission.
 *
 * The client sends only evidence. The server recomputes the distance from the
 * configured office, checks the accuracy and freshness, and decides the work
 * mode itself. A client-provided WFH flag is never accepted.
 */
public record AttendanceMarkRequest(

        @NotNull(message = "Location is required to mark office attendance")
        @Min(value = -90, message = "Latitude must be between -90 and 90")
        @Max(value = 90, message = "Latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "Location is required to mark office attendance")
        @Min(value = -180, message = "Longitude must be between -180 and 180")
        @Max(value = 180, message = "Longitude must be between -180 and 180")
        Double longitude,

        @NotNull(message = "Location accuracy is required")
        @Positive(message = "Location accuracy must be positive")
        @Max(value = 100000, message = "Location accuracy is not a valid value")
        Double accuracyMeters,

        /** Browser capture time in epoch milliseconds. */
        @NotNull(message = "Location capture time is required")
        @Positive(message = "Location capture time is not valid")
        Long capturedAtEpochMillis
) {
}
