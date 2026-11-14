
package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @Column(length = 50)
    private String contact;

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
