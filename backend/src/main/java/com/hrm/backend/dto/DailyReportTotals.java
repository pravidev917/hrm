package com.hrm.backend.dto;

public record DailyReportTotals(
        Long reportCount,
        Long profilesSubmitted,
        Long candidatesSourced,
        Long candidatesContacted,
        Long connectedCount) {
}
