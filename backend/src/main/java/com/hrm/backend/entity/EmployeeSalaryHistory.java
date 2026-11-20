package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Salary effective from the first day of a month.
 *
 * Monthly targets are salary x 3 for the reporting month, so a salary change in
 * a later month must not rewrite an earlier month's target. `salary` may be null
 * when an administrator explicitly cleared it; that month then reports
 * "Target not configured" instead of falling back to an older value.
 */
@Entity
@Table(
        name = "employee_salary_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_employee_salary_history_month",
                columnNames = {"employee_id", "effective_month"}))
@Getter
@Setter
@NoArgsConstructor
public class EmployeeSalaryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_salary_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Employee employee;

    @Column(name = "effective_month", nullable = false)
    private LocalDate effectiveMonth;

    @Column(precision = 12, scale = 2)
    private BigDecimal salary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
