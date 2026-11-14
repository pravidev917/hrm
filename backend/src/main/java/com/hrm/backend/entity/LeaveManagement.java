
package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_management")
@Getter
@Setter
@NoArgsConstructor
public class LeaveManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_id")
    private Long leaveId;

    // Employee who applied for leave
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Employee employee;

    // Admin who approved or rejected the request
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Admin approvedBy;

    // Leave period
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // Calculated by backend
    @Column(name = "number_of_days",
            nullable = false,
            precision = 5,
            scale = 2)
    private BigDecimal numberOfDays;

    // FIRST_HALF, SECOND_HALF or null for full-day leave
    @Column(name = "half_day_period", length = 20)
    private String halfDayPeriod;

    // Employee's reason
    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "leave_type", nullable = false, length = 50)
    private String type;

    // PENDING, APPROVED, REJECTED, CANCELLED
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    // Admin's decision remarks
    @Column(name = "admin_remarks", columnDefinition = "TEXT")
    private String adminRemarks;

    // When admin reviewed the request
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
