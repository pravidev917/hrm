package com.hrm.backend.dto;

import java.math.BigDecimal;

public record WeeklyReportTotals(
        Long reportCount,
        Long profilesSubmitted,
        Long interviewsScheduled,
        Long finalSelections,
        Long joined,
        BigDecimal revenueGenerated) {
}
