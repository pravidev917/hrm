
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

    List<WeeklyReport> findByEmployeeEmployeeIdAndWeekStartDateBetween(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate);

    List<WeeklyReport> findByEmployeeAdminAdminId(Long adminId);

    List<WeeklyReport> findByEmployeeAdminAdminIdAndWeekStartDateBetween(
            Long adminId,
            LocalDate startDate,
            LocalDate endDate);

    Optional<WeeklyReport> findByEmployeeEmployeeIdAndWeekStartDateAndWeekEndDate(
            Long employeeId,
            LocalDate weekStartDate,
            LocalDate weekEndDate);

    boolean existsByEmployeeEmployeeIdAndWeekStartDateAndWeekEndDate(
            Long employeeId,
            LocalDate weekStartDate,
            LocalDate weekEndDate);
}
