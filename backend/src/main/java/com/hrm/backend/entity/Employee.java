
package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long employeeId;

    // Admin relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Admin admin;

    // Employee details
    @Column(name = "employee_name", nullable = false, length = 100)
    private String employeeName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    // Employee job designation
    @Column(nullable = false, length = 50)
    private String role;

    // Phone number. Also usable as a sign-in identifier, so it is unique.
    @Column(length = 50, unique = true)
    private String contact;

    /**
     * Human-readable employee ID (for example EMP-0007) assigned by the administrator.
     * Employees sign in with this, their email or their phone number.
     * Nullable only for rows created before the field existed.
     */
    @Column(name = "employee_code", unique = true, length = 40)
    private String employeeCode;

    @Column(length = 100)
    private String department;

    @Column(precision = 12, scale = 2)
    private BigDecimal salary;

    // Account approval status
    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus = "PENDING";

    // HRM relationships
    @JsonIgnore
    @OneToMany(mappedBy = "employee")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Attendance> attendanceRecords = new ArrayList<>();
    @JsonIgnore
    @OneToMany(mappedBy = "employee")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LeaveManagement> leaveRequests = new ArrayList<>();
    @JsonIgnore
    @OneToMany(mappedBy = "employee")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DailyReport> dailyReports = new ArrayList<>();
    @JsonIgnore
    @OneToMany(mappedBy = "employee")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<WeeklyReport> weeklyReports = new ArrayList<>();
    @JsonIgnore
    @OneToMany(mappedBy = "employee")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Performance> performanceRecords = new ArrayList<>();
    @JsonIgnore
    @OneToMany(mappedBy = "employee")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Followup> followups = new ArrayList<>();
    @JsonIgnore
    @OneToMany(mappedBy = "employee")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Feedback> feedbackRecords = new ArrayList<>();

    // Optional profile dates used by the calendar (birthdays / work anniversaries).
    // Nullable and additive: existing rows stay valid with ddl-auto=update.
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
