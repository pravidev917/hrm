
package com.hrm.backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class WeeklyReportResponseDTO {

    private Long weeklyReportId;

    private Long employeeId;

    private String employeeName;

    private Long clientId;

    private String clientName;

    private Integer weekNumber;

    private LocalDate weekStartDate;

    private LocalDate weekEndDate;

    private Integer profilesSubmitted;

    private Integer interviewsScheduled;

    private Integer finalSelections;

    private Integer joined;

    private BigDecimal revenueGenerated;
    private String candidateName;
    private String position;
    private String interviewStatus;
}
