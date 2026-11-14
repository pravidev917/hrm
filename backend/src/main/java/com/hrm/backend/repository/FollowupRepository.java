
package com.hrm.backend.repository;

import com.hrm.backend.entity.Followup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowupRepository extends JpaRepository<Followup, Long> {

    // Employee-specific records
    List<Followup> findByEmployee_EmployeeIdOrderByFollowupIdDesc(
            Long employeeId
    );

    Optional<Followup> findByFollowupIdAndEmployee_EmployeeId(
            Long followupId,
            Long employeeId
    );

    // All follow-ups belonging to an admin's employees
    List<Followup> findByEmployee_Admin_AdminIdOrderByFollowupIdDesc(
            Long adminId
    );

    Optional<Followup> findByFollowupIdAndEmployee_Admin_AdminId(
            Long followupId,
            Long adminId
    );
}
