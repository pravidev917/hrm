
package com.hrm.backend.repository;

import com.hrm.backend.entity.Performance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PerformanceRepository
        extends JpaRepository<Performance, Long> {

    List<Performance> findByEmployee_EmployeeIdOrderByPerformanceIdDesc(
            Long employeeId
    );

    Optional<Performance> findByPerformanceIdAndEmployee_EmployeeId(
            Long performanceId,
            Long employeeId
    );

    @Query("""
            SELECT x FROM Performance x
            WHERE x.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY x.performanceId DESC
            """)
    List<Performance> findByEmployeeInCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT x FROM Performance x
            WHERE x.performanceId = :performanceId
              AND x.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    Optional<Performance> findByIdInCompanyOf(
            @Param("performanceId") Long performanceId,
            @Param("adminId") Long adminId
    );

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployee_EmployeeId(Long employeeId);
}
