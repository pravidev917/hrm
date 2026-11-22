
package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
// An employee may file more than one weekly report for the same week (one per
// client, or one per candidate placed), so there is no unique constraint here.
@Table(name = "weekly_reports")
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

    /**
     * Optional client the week's results belong to. Nullable on purpose: weeks
     * filed before client selection existed stay valid.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Client client;

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

    /** Candidate the week's placement refers to (free text, reference column). */
    @Column(name = "candidate_name", length = 150)
    private String candidateName;

    /** Position the candidate was put forward for; defaults to the client's position. */
    @Column(length = 150)
    private String position;

    /** Where the candidate stands: INTERVIEW_SCHEDULED, SELECTED, JOINED, REJECTED, ON_HOLD. */
    @Column(name = "interview_status", length = 40)
    private String interviewStatus;
}
