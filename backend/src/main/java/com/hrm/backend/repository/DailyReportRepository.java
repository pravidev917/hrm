
package com.hrm.backend.repository;

import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.dto.DailyReportTotals;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    // Employee dashboard totals (database-side aggregation)
    @Query("""
            SELECT new com.hrm.backend.dto.DailyReportTotals(
                COUNT(r),
                COALESCE(SUM(r.profilesSubmitted), 0),
                COALESCE(SUM(r.candidatesSourced), 0),
                COALESCE(SUM(r.candidatesContacted), 0),
                COALESCE(SUM(r.connectedCount), 0))
            FROM DailyReport r
            WHERE r.employee.employeeId = :employeeId
            """)
    DailyReportTotals getDashboardTotals(@Param("employeeId") Long employeeId);

    // Employee reports
    List<DailyReport> findByEmployeeEmployeeId(Long employeeId);

    // Reports by date
    List<DailyReport> findByReportDate(LocalDate reportDate);

    // Employee reports within date range
    List<DailyReport> findByEmployeeEmployeeIdAndReportDateBetween(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate
    );

    // Check duplicate report for employee and date
    Optional<DailyReport> findByEmployeeEmployeeIdAndReportDate(
            Long employeeId,
            LocalDate reportDate
    );

    // Reports belonging to a particular admin
    List<DailyReport> findByEmployeeAdminAdminId(Long adminId);

    // Reports belonging to an employee and client
    List<DailyReport> findByEmployeeEmployeeIdAndClientClientId(
            Long employeeId,
            Long clientId
    );

    // Reports belonging to an admin within date range
    List<DailyReport> findByEmployeeAdminAdminIdAndReportDateBetween(
            Long adminId,
            LocalDate startDate,
            LocalDate endDate
    );
}
