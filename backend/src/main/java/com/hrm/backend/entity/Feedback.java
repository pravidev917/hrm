package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A candidate sourced by an employee for a client — the sourcing pipeline record.
 *
 * Replaces the earlier free-text feedback note with a structured tracker: the
 * candidate's details, their interview progress and the final outcome. The legacy
 * `client`, `comments` and `feedbackReceived` columns are still mapped so existing
 * rows keep their data; `client` is superseded by `clientName` and
 * `feedbackReceived` by `status`.
 */
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

    // ---- legacy columns, kept so existing rows are not lost ----

    /** Superseded by {@link #clientName}. */
    @Column(name = "client", length = 150)
    private String client;

    /** Superseded by {@link #status}. */
    @Column(name = "feedback_received", length = 30)
    private String feedbackReceived;

    // ---- candidate details ----

    @Column(name = "candidate_name", nullable = false, length = 100)
    private String candidateName;

    @Column(name = "candidate_email", length = 150)
    private String candidateEmail;

    @Column(name = "contact_number", length = 30)
    private String contactNumber;

    @Column(name = "current_location", length = 100)
    private String currentLocation;

    @Column(name = "preferred_location", length = 100)
    private String preferredLocation;

    @Column(name = "current_ctc", precision = 12, scale = 2)
    private BigDecimal currentCtc;

    @Column(name = "expected_ctc", precision = 12, scale = 2)
    private BigDecimal expectedCtc;

    @Column(name = "current_company", length = 150)
    private String currentCompany;

    @Column(name = "current_designation", length = 100)
    private String currentDesignation;

    /** Notice period in days. */
    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    // ---- interview / client ----

    @Column(name = "hr_name", length = 100)
    private String hrName;

    @Column(name = "client_name", length = 150)
    private String clientName;

    @Column(name = "interview_scheduled")
    private Boolean interviewScheduled;

    @Column(name = "interview_scheduled_date")
    private LocalDate interviewScheduledDate;

    @Column(name = "interview_attended")
    private Boolean interviewAttended;

    // ---- outcome ----

    /**
     * Pipeline stage: SOURCED, INTERVIEW_SCHEDULED, INTERVIEW_ATTENDED,
     * SELECTED, JOINED, REJECTED, DROPPED.
     */
    @Column(length = 30)
    private String status;

    /** True once the joined candidate has left a public/Google review. */
    @Column(name = "google_feedback_given")
    private Boolean googleFeedbackGiven;

    @Column(columnDefinition = "TEXT")
    private String comments;
}
