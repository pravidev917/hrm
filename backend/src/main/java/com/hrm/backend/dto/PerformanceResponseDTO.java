
package com.hrm.backend.dto;

import com.hrm.backend.entity.Performance;
import java.math.BigDecimal;

public record PerformanceResponseDTO(
        Long performanceId,
        Long employeeId,
        String employeeName,
        String dailyPerformance,
        String weeklyPerformance,
        BigDecimal overallRating
) {
    public static PerformanceResponseDTO from(Performance performance) {
        return new PerformanceResponseDTO(
                performance.getPerformanceId(),
                performance.getEmployee().getEmployeeId(),
                performance.getEmployee().getEmployeeName(),
                performance.getDailyPerformance(),
                performance.getWeeklyPerformance(),
                performance.getOverallRating()
        );
    }
}
