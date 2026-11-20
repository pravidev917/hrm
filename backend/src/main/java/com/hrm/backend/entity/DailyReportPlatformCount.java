package com.hrm.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * Profiles sourced from one platform in a daily report.
 *
 * A report may span several platforms (for example 3 LinkedIn + 2 referrals).
 * The sum of these rows must equal the report's candidatesSourced count; the
 * service enforces that so platform totals always reconcile with the report.
 */
@Entity
@Table(
        name = "daily_report_platform_counts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_report_platform_counts",
                columnNames = {"daily_report_id", "platform"}))
@Getter
@Setter
@NoArgsConstructor
public class DailyReportPlatformCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_report_platform_count_id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_report_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DailyReport dailyReport;

    @Column(name = "platform", nullable = false, length = 80)
    private String platform;

    @Column(name = "profile_count", nullable = false)
    private Integer profileCount = 0;
}
