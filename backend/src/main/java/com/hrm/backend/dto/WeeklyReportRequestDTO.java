
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
}
