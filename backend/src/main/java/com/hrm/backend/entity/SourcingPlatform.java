package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Company-managed sourcing platform option (LinkedIn, Naukri, referral, ...).
 *
 * Reports and candidate records store the platform name as text, not a foreign
 * key. Renaming or deactivating an option therefore never invalidates history.
 */
@Entity
@Table(
        name = "sourcing_platforms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sourcing_platforms_company_name",
                columnNames = {"company_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class SourcingPlatform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sourcing_platform_id")
    private Long sourcingPlatformId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Company company;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

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
