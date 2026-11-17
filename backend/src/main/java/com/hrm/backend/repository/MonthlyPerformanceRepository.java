package com.hrm.backend.repository;

import com.hrm.backend.entity.MonthlyPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MonthlyPerformanceRepository
        extends JpaRepository<MonthlyPerformance, Long> {

    List<MonthlyPerformance> findByEmployeeId(Long employeeId);

    List<MonthlyPerformance> findByYearAndMonth(
            Integer year, Integer month);

    List<MonthlyPerformance> findByYear(Integer year);

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployeeId(Long employeeId);
}
