package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;

@Entity
@Immutable
@Table(name = "vw_monthly_performance")
@Getter @Setter @NoArgsConstructor
public class MonthlyPerformance {
    // This entity maps a read-only SQL VIEW, not a manually maintained table.
    @Id
    @Column(name = "dashboard_id")
    private Long dashboardId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "month")
    private Integer month;

    @Column(name = "year")
    private Integer year;

    @Column(name = "total_profiles_submitted")
    private Long totalProfilesSubmitted;

    @Column(name = "total_interviews_scheduled")
    private Long totalInterviewsScheduled;

    @Column(name = "total_selections")
    private Long totalSelections;

    @Column(name = "total_joined")
    private Long totalJoined;

    @Column(name = "total_revenue", precision = 12, scale = 2)
    private BigDecimal totalRevenue;

    @Column(name = "overall_rating", precision = 3, scale = 2)
    private BigDecimal overallRating;
}
