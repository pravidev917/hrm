
package com.hrm.backend.dto;

import java.math.BigDecimal;

public record PerformanceRequestDTO(
        String dailyPerformance,
        String weeklyPerformance,
        BigDecimal overallRating
) {
}
