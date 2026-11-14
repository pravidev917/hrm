
package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id")
    private Long clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Admin admin;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Column(name = "profiles_submitted")
    private Integer profilesSubmitted = 0;

    @Column(name = "interviews_conducted")
    private Integer interviewsConducted = 0;

    @Column(name = "selected_candidates")
    private Integer selectedCandidates = 0;

    @Column(name = "joining_details", columnDefinition = "TEXT")
    private String joiningDetails;

    @Column(name = "hr_name", length = 100)
    private String hrName;

    @Column(name = "hr_contact_details", length = 150)
    private String hrContactDetails;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";
}
