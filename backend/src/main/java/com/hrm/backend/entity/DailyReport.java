
package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "daily_reports")
@Getter
@Setter
@NoArgsConstructor
public class DailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_report_id")
    private Long dailyReportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Client client;

    @Column(name = "profiles_submitted", nullable = false)
    private Integer profilesSubmitted = 0;

    @Column(name = "candidates_sourced", nullable = false)
    private Integer candidatesSourced = 0;

    @Column(name = "candidates_contacted", nullable = false)
    private Integer candidatesContacted = 0;

    @Column(name = "connected_count", nullable = false)
    private Integer connectedCount = 0;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
}
