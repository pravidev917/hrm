package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "admins")
@Getter
@Setter
@NoArgsConstructor
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_name", nullable = false, length = 100)
    private String adminName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;


    @Column(name = "role_access_level", nullable = false, length = 50)
    private String roleAccessLevel = "ADMIN";

    @Column(name = "contact_details", length = 50)
    private String contactDetails;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /**
     * The company this administrator works for. Assigned when the account is
     * created (creator's company) and never editable through the API, so an
     * administrator cannot move itself or another account into another company.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @JsonIgnore
    @OneToMany(mappedBy = "admin")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Client> clients = new ArrayList<>();

    @OneToMany(mappedBy = "approvedBy")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LeaveManagement> reviewedLeaves = new ArrayList<>();

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