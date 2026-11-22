package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "clients",
    uniqueConstraints = {
        // Client ID is unique per organisation: two admins may use the same code.
        @UniqueConstraint(name = "uk_clients_admin_code", columnNames = {"admin_id", "client_code"})
    }
)
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

    /** Human-readable client ID chosen by the administrator (for example CL-1001). */
    @Column(name = "client_code", length = 40)
    private String clientCode;

    @Column(name = "vendor", length = 150)
    private String vendor;

    @Column(name = "bdm_name", length = 100)
    private String bdmName;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Column(name = "position", length = 150)
    private String position;

    @Column(name = "contact_number", length = 50)
    private String contactNumber;

    /** Client HR contact name. Kept under its original column name. */
    @Column(name = "hr_name", length = 100)
    private String hrName;

    @Column(name = "number_of_positions")
    private Integer numberOfPositions;

    @Column(name = "salary_offered", precision = 12, scale = 2)
    private BigDecimal salaryOffered;

    /**
     * How the client pays: a fixed amount for every candidate placed
     * (OFFERED_PER_CANDIDATE) or the annual salary the position carries
     * (SALARY_BASED). The matching figure is compensationValue.
     */
    @Column(name = "compensation_type", nullable = false, length = 30)
    private String compensationType = "OFFERED_PER_CANDIDATE";

    @Column(name = "compensation_value", precision = 12, scale = 2)
    private BigDecimal compensationValue;

    @Column(name = "percentage_per_candidate", precision = 5, scale = 2)
    private BigDecimal percentagePerCandidate;

    /** Explicit budget for one placement at this position. */
    @Column(name = "budget_per_candidate", precision = 12, scale = 2)
    private BigDecimal budgetPerCandidate;

    @Column(name = "tax", precision = 5, scale = 2)
    private BigDecimal tax;

    @Column(name = "profiles_submitted")
    private Integer profilesSubmitted = 0;

    @Column(name = "interviews_conducted")
    private Integer interviewsConducted = 0;

    @Column(name = "selected_candidates")
    private Integer selectedCandidates = 0;

    @Column(name = "joining_details", columnDefinition = "TEXT")
    private String joiningDetails;

    @Column(name = "hr_contact_details", length = 150)
    private String hrContactDetails;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";
}
