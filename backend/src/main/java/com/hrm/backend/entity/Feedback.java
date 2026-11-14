package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "feedback")
@Getter @Setter @NoArgsConstructor
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long feedbackId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private Employee employee;

    // The source specification stores Client as text, not as client_id.
    @Column(name = "client", length = 150)
    private String client;

    @Column(name = "candidate_name", nullable = false, length = 100)
    private String candidateName;

    @Column(name = "feedback_received", nullable = false, length = 30)
    private String feedbackReceived;

    @Column(columnDefinition = "TEXT")
    private String comments;
}
