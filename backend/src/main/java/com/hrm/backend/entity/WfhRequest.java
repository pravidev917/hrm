package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An employee's request to work from home for a date range.
 *
 * Only an APPROVED request whose range covers the company's today may let the
 * employee mark attendance without office location verification. Every other
 * status (PENDING, REJECTED, CANCELLED, expired, future) is ignored by the
 * attendance check.
 */
@Entity
@Table(
        name = "wfh_requests",
        indexes = {
                @Index(name = "idx_wfh_requests_employee", columnList = "employee_id"),
                @Index(name = "idx_wfh_requests_status_dates", columnList = "status, start_date, end_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class WfhRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wfh_id")
    private Long wfhId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Employee employee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    /** PENDING, APPROVED, REJECTED or CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Admin reviewedBy;

    @Column(name = "admin_comment", columnDefinition = "TEXT")
    private String adminComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
