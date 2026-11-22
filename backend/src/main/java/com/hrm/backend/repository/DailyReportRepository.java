
package com.hrm.backend.repository;

import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.dto.DailyReportDayTotals;
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

    // Reports of the administrator's whole company (shared records)
    @Query("""
            SELECT r FROM DailyReport r
            WHERE r.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    List<DailyReport> findByEmployeeInCompanyOf(@Param("adminId") Long adminId);

    // Reports belonging to an employee and client
    List<DailyReport> findByEmployeeEmployeeIdAndClientClientId(
            Long employeeId,
            Long clientId
    );

    // Reports of the administrator's company within a date range
    @Query("""
            SELECT r FROM DailyReport r
            WHERE r.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND r.reportDate BETWEEN :startDate AND :endDate
            """)
    List<DailyReport> findByEmployeeInCompanyOfAndReportDateBetween(
            @Param("adminId") Long adminId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Every report of the company in a date range with the employee and the
     * per-platform counts loaded, so analytics can read them after the transaction.
     */
    @Query("""
            SELECT DISTINCT r FROM DailyReport r
              JOIN FETCH r.employee e
              LEFT JOIN FETCH r.platformCounts pc
            WHERE e.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND r.reportDate BETWEEN :startDate AND :endDate
            ORDER BY r.reportDate ASC
            """)
    List<DailyReport> findForAnalyticsInCompanyOf(
            @Param("adminId") Long adminId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /** First day the company ever filed a daily report; empty when there are none. */
    @Query("""
            SELECT MIN(r.reportDate) FROM DailyReport r
            WHERE r.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    Optional<LocalDate> findFirstReportDateInCompanyOf(@Param("adminId") Long adminId);

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployeeEmployeeId(Long employeeId);

    // Used before deleting a client: a client that reports point at is retired, not removed.
    long countByClientClientId(Long clientId);

    /**
     * Reports, distinct employees and resumes submitted across one company on
     * one date. One query, counted in the database, used by the dashboard card.
     */
    @Query("""
            SELECT new com.hrm.backend.dto.DailyReportDayTotals(
                COUNT(r),
                COUNT(DISTINCT r.employee.employeeId),
                COALESCE(SUM(r.profilesSubmitted), 0))
            FROM DailyReport r
            WHERE r.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND r.reportDate = :date
            """)
    DailyReportDayTotals getCompanyDayTotals(
            @Param("adminId") Long adminId,
            @Param("date") LocalDate date);

    /** Same shape as the day totals, for a date range (used for month-to-date figures). */
    @Query("""
            SELECT new com.hrm.backend.dto.DailyReportDayTotals(
                COUNT(r),
                COUNT(DISTINCT r.employee.employeeId),
                COALESCE(SUM(r.profilesSubmitted), 0))
            FROM DailyReport r
            WHERE r.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND r.reportDate BETWEEN :from AND :to
            """)
    DailyReportDayTotals getCompanyRangeTotals(
            @Param("adminId") Long adminId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
