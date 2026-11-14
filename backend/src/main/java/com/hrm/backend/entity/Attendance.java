package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

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
}
