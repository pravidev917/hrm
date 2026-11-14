package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "followups")
@Getter @Setter @NoArgsConstructor
public class Followup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "followup_id")
    private Long followupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private Employee employee;

    @Column(name = "candidate_name", nullable = false, length = 100)
    private String candidateName;

    // The source specification stores Client as text, not as client_id.
    @Column(name = "client", length = 150)
    private String client;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Column(name = "joining_status", length = 30)
    private String joiningStatus;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
