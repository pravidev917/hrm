package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One Log In -> Log Out pair of an employee.
 *
 * Timestamps are UTC instants written from the server clock: nothing in the request
 * body can move them. {@code attendanceDate} is the company-local date of the login,
 * so a session that crosses midnight stays on the day it started.
 *
 * Status values are strict:
 *   ACTIVE      the employee is logged in right now
 *   CLOSED      the employee logged out, logoutAt and durationSeconds are set
 *   INCOMPLETE  the day rolled over without a logout; logoutAt stays NULL on purpose
 *               because inventing a logout time would invent working hours too
 *
 * "At most one ACTIVE session per employee" is enforced by a partial unique index
 * (uk_attendance_sessions_one_active), so concurrent Log In requests cannot both win.
 */
@Entity
@Table(name = "attendance_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceSession {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_INCOMPLETE = "INCOMPLETE";

    public static final String CLOSED_BY_EMPLOYEE = "EMPLOYEE";
    public static final String CLOSED_BY_SYSTEM = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "login_at", nullable = false)
    private Instant loginAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "logout_at")
    private Instant logoutAt;

    /** Whole seconds between loginAt and logoutAt; null while the session is open. */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "closed_by", length = 20)
    private String closedBy;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /** True for a session that was left open on an earlier day ("forgot to log out"). */
    public boolean isStale(LocalDate today) {
        return isActive() && attendanceDate.isBefore(today);
    }
}
