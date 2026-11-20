package com.hrm.backend.repository;

import com.hrm.backend.entity.EmployeeSalaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeSalaryHistoryRepository
        extends JpaRepository<EmployeeSalaryHistory, Long> {

    @Query("""
            SELECT h FROM EmployeeSalaryHistory h
            WHERE h.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND h.effectiveMonth <= :upToMonth
            ORDER BY h.employee.employeeId ASC, h.effectiveMonth ASC
            """)
    List<EmployeeSalaryHistory> findCompanyHistoryUpTo(
            @Param("adminId") Long adminId,
            @Param("upToMonth") LocalDate upToMonth);

    List<EmployeeSalaryHistory> findByEmployeeEmployeeIdOrderByEffectiveMonthAsc(
            Long employeeId);

    Optional<EmployeeSalaryHistory> findByEmployeeEmployeeIdAndEffectiveMonth(
            Long employeeId, LocalDate effectiveMonth);

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployeeEmployeeId(Long employeeId);
}
