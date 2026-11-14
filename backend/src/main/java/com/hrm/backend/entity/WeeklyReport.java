
package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "weekly_reports",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"employee_id", "week_start_date", "week_end_date"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class WeeklyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weekly_report_id")
    private Long weeklyReportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Employee employee;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(name = "profiles_submitted", nullable = false)
    private Integer profilesSubmitted = 0;

    @Column(name = "interviews_scheduled", nullable = false)
    private Integer interviewsScheduled = 0;

    @Column(name = "final_selections", nullable = false)
    private Integer finalSelections = 0;

    @Column(nullable = false)
    private Integer joined = 0;

    @Column(name = "revenue_generated", precision = 12, scale = 2)
    private BigDecimal revenueGenerated = BigDecimal.ZERO;
}
