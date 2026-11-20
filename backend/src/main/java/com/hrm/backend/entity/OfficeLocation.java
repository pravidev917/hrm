package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The physical office of one company.
 *
 * One row per company: every administrator of the company sees and edits the
 * same settings, and every employee of the company is verified against them.
 * The company is assigned from the authenticated administrator, never from a
 * request body.
 *
 * The timezone lives here because attendance dates are company-local: an
 * employee marking attendance near midnight must be recorded on the company's
 * calendar day, not on the server's or the browser's.
 */
@Entity
@Table(
        name = "office_locations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_office_locations_company",
                columnNames = {"company_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class OfficeLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "office_location_id")
    private Long officeLocationId;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Company company;

    @Column(name = "office_name", nullable = false, length = 150)
    private String officeName;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    /** Permitted distance from the office in metres. */
    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    /** IANA zone id (for example Asia/Kolkata) used for attendance dates. */
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    /** Last administrator who changed the settings (audit attribution). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_admin_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Admin updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
