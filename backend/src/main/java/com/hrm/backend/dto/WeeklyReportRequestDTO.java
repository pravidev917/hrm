
package com.hrm.backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class WeeklyReportRequestDTO {

    private LocalDate reportDate;

    private Integer profilesSubmitted = 0;

    private Integer interviewsScheduled = 0;

    private Integer finalSelections = 0;

    private Integer joined = 0;

    private BigDecimal revenueGenerated = BigDecimal.ZERO;

    /**
     * Optional client the week belongs to. Null leaves the report without a
     * client (weeks filed before client selection existed).
     */
    private Long clientId;

    /** Candidate the week refers to (optional, shown in the weekly report table). */
    private String candidateName;

    /** Position put forward; falls back to the client's position when empty. */
    private String position;

    /** INTERVIEW_SCHEDULED, SELECTED, JOINED, REJECTED or ON_HOLD. */
    private String interviewStatus;
}
