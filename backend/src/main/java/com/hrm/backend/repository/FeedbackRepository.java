
package com.hrm.backend.repository;

import com.hrm.backend.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // Employee: retrieve own feedback
    List<Feedback> findByEmployee_EmployeeIdOrderByFeedbackIdDesc(
            Long employeeId
    );

    Optional<Feedback> findByFeedbackIdAndEmployee_EmployeeId(
            Long feedbackId,
            Long employeeId
    );

    // Admin: feedback across the administrator's whole company (shared records)
    @Query("""
            SELECT x FROM Feedback x
            WHERE x.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY x.feedbackId DESC
            """)
    List<Feedback> findByEmployeeInCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT x FROM Feedback x
            WHERE x.feedbackId = :feedbackId
              AND x.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    Optional<Feedback> findByIdInCompanyOf(
            @Param("feedbackId") Long feedbackId,
            @Param("adminId") Long adminId
    );

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployee_EmployeeId(Long employeeId);
}
