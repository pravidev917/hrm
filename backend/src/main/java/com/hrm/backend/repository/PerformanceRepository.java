
package com.hrm.backend.repository;

import com.hrm.backend.entity.Performance;
import org.springframework.data.jpa.repository.JpaRepository;

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

    List<Performance> findByEmployee_Admin_AdminIdOrderByPerformanceIdDesc(
            Long adminId
    );

    Optional<Performance> findByPerformanceIdAndEmployee_Admin_AdminId(
            Long performanceId,
            Long adminId
    );

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployee_EmployeeId(Long employeeId);
}
