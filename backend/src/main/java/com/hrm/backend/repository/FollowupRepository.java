
package com.hrm.backend.repository;

import com.hrm.backend.entity.Followup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // All follow-ups of the administrator's company (shared records)
    @Query("""
            SELECT x FROM Followup x
            WHERE x.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY x.followupId DESC
            """)
    List<Followup> findByEmployeeInCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT x FROM Followup x
            WHERE x.followupId = :followupId
              AND x.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    Optional<Followup> findByIdInCompanyOf(
            @Param("followupId") Long followupId,
            @Param("adminId") Long adminId
    );

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployee_EmployeeId(Long employeeId);
}
