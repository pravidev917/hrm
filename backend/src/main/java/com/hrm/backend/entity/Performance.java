package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "performance")
@Getter @Setter @NoArgsConstructor
public class Performance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id")
    private Long performanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private Employee employee;

    @Column(name = "daily_performance", columnDefinition = "TEXT")
    private String dailyPerformance;

    @Column(name = "weekly_performance", columnDefinition = "TEXT")
    private String weeklyPerformance;

    @Column(name = "overall_rating", precision = 3, scale = 2)
    private BigDecimal overallRating;
}
