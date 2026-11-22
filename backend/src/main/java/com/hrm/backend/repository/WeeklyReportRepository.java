
package com.hrm.backend.repository;

import com.hrm.backend.entity.WeeklyReport;
import com.hrm.backend.dto.WeeklyReportTotals;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyReportRepository
        extends JpaRepository<WeeklyReport, Long> {

    @Query("""
            SELECT new com.hrm.backend.dto.WeeklyReportTotals(
                COUNT(r),
                COALESCE(SUM(r.profilesSubmitted), 0),
                COALESCE(SUM(r.interviewsScheduled), 0),
                COALESCE(SUM(r.finalSelections), 0),
                COALESCE(SUM(r.joined), 0),
                COALESCE(SUM(r.revenueGenerated), 0))
            FROM WeeklyReport r
            WHERE r.employee.employeeId = :employeeId
            """)
    WeeklyReportTotals getDashboardTotals(@Param("employeeId") Long employeeId);

    List<WeeklyReport> findByEmployeeEmployeeId(Long employeeId);

    Optional<WeeklyReport> findByWeeklyReportIdAndEmployeeEmployeeId(
            Long weeklyReportId, Long employeeId);

    // Several reports may share a week (one per client, or one per candidate placed),
    // so every query here returns a list and callers must sum the rows they care about.
    List<WeeklyReport> findByEmployeeEmployeeIdAndWeekStartDateBetween(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate);

    /** Weekly reports of the administrator's whole company (shared records). */
    @Query("""
            SELECT r FROM WeeklyReport r
            WHERE r.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    List<WeeklyReport> findByEmployeeInCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT r FROM WeeklyReport r
            WHERE r.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND r.weekStartDate BETWEEN :startDate AND :endDate
            """)
    List<WeeklyReport> findByEmployeeInCompanyOfAndWeekStartDateBetween(
            @Param("adminId") Long adminId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployeeEmployeeId(Long employeeId);

    // Used before deleting a client: a client that reports point at is retired, not removed.
    long countByClientClientId(Long clientId);
}
