package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance",
       uniqueConstraints = @UniqueConstraint(name = "uk_attendance_employee_date",
                                             columnNames = {"employee_id", "attendance_date"}))
@Getter @Setter @NoArgsConstructor
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long attendanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false, length = 20)
    private String status;

    /**
     * OFFICE or WFH for a PRESENT record, null for ABSENT and historical rows.
     * Set by the server from verified evidence, never from a request flag.
     */
    @Column(name = "work_mode", length = 20)
    private String workMode;

    /** Verified distance from the configured office, in metres. */
    @Column(name = "distance_meters", precision = 8, scale = 2)
    private BigDecimal distanceMeters;

    /** Browser-reported accuracy, kept as evidence of the quality of the fix. */
    @Column(name = "accuracy_meters", precision = 8, scale = 2)
    private BigDecimal accuracyMeters;

    /**
     * Time the browser captured the position (stored as UTC without a zone).
     * Only the server-side freshness window uses it; it is not returned to clients.
     */
    @Column(name = "location_captured_at")
    private LocalDateTime locationCapturedAt;

    /** Server time at which the attendance record was created/changed. */
    @Column(name = "marked_at")
    private LocalDateTime markedAt;

    /** Approved WFH request used as evidence for a WFH attendance record. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wfh_request_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private WfhRequest wfhRequest;
}
