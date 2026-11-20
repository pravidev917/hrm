package com.hrm.backend.repository;

import com.hrm.backend.entity.LeaveManagement;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LeaveManagementRepository
        extends JpaRepository<LeaveManagement, Long> {

    // --------------------------------------------------
    // EMPLOYEE LEAVE HISTORY
    // --------------------------------------------------

    List<LeaveManagement>
    findByEmployeeEmployeeIdOrderByStartDateDesc(Long employeeId);

    // --------------------------------------------------
    // ADMIN LEAVE HISTORY
    // --------------------------------------------------

    /** Leave requests of the administrator's whole company (shared records). */
    @Query("""
            SELECT l FROM LeaveManagement l
            WHERE l.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY l.startDate DESC
            """)
    List<LeaveManagement> findByEmployeeInCompanyOfOrderByStartDateDesc(
            @Param("adminId") Long adminId);

    // --------------------------------------------------
    // STATUS
    // --------------------------------------------------

    List<LeaveManagement>
    findByStatusOrderByStartDateAsc(String status);

    List<LeaveManagement>
    findByEmployeeEmployeeIdAndStatus(
            Long employeeId,
            String status);

    // --------------------------------------------------
    // EMPLOYEE OVERLAP CHECK
    // --------------------------------------------------

    List<LeaveManagement>
    findByEmployeeEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId,
            List<String> statuses,
            LocalDate requestedEndDate,
            LocalDate requestedStartDate);

    // --------------------------------------------------
    // EMPLOYEE DATE RANGE
    // --------------------------------------------------

    List<LeaveManagement>
    findByEmployeeEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId,
            LocalDate endDate,
            LocalDate startDate);

    // --------------------------------------------------
    // ADMIN DATE RANGE
    // --------------------------------------------------

    @Query("""
            SELECT l FROM LeaveManagement l
            WHERE l.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND l.startDate <= :endDate
              AND l.endDate >= :startDate
            """)
    List<LeaveManagement>
    findByEmployeeInCompanyOfAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            @Param("adminId") Long adminId,
            @Param("endDate") LocalDate endDate,
            @Param("startDate") LocalDate startDate);

    // --------------------------------------------------
    // EMPLOYEE DASHBOARD: APPROVED LEAVE DAYS
    @Query("""
            SELECT COALESCE(SUM(l.numberOfDays), 0)
            FROM LeaveManagement l
            WHERE l.employee.employeeId = :employeeId
              AND l.status = 'APPROVED'
            """)
    BigDecimal sumApprovedDaysByEmployeeId(
            @Param("employeeId") Long employeeId);

    // STATUS COUNT
    // --------------------------------------------------

    long countByEmployeeEmployeeIdAndStatus(
            Long employeeId,
            String status);

    // --------------------------------------------------
    // ADMIN DASHBOARD (scoped to one admin's employees)
    // --------------------------------------------------

    @Query("""
            SELECT COUNT(l) FROM LeaveManagement l
            WHERE l.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND l.status = :status
            """)
    long countByEmployeeInCompanyOfAndStatus(
            @Param("adminId") Long adminId, @Param("status") String status);

    @Query("""
            SELECT COUNT(l) FROM LeaveManagement l
            WHERE l.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    long countByEmployeeInCompanyOf(@Param("adminId") Long adminId);

    /** Leave requests this administrator approved or rejected (audit trail). */
    long countByApprovedByAdminId(Long adminId);

    @Query("""
            SELECT COUNT(l) FROM LeaveManagement l
            WHERE l.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND l.startDate >= :fromDate
            """)
    long countByEmployeeInCompanyOfAndStartDateGreaterThanEqual(
            @Param("adminId") Long adminId, @Param("fromDate") LocalDate fromDate);

    @Query("""
            SELECT COALESCE(SUM(l.numberOfDays), 0)
            FROM LeaveManagement l
            WHERE l.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND l.status = 'APPROVED'
              AND l.startDate >= :fromDate
            """)
    BigDecimal sumApprovedDaysInCompanyOf(
            @Param("adminId") Long adminId,
            @Param("fromDate") LocalDate fromDate);

    /** Next pending requests of the company's review queue, oldest first. */
    @Query("""
            SELECT l FROM LeaveManagement l
            WHERE l.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND l.status = :status
            ORDER BY l.startDate ASC
            """)
    List<LeaveManagement> findTop5PendingInCompanyOf(
            @Param("adminId") Long adminId, @Param("status") String status, Pageable pageable);

    // --------------------------------------------------
    // LOCK LEAVE FOR ADMIN REVIEW
    // --------------------------------------------------

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT l
            FROM LeaveManagement l
            WHERE l.leaveId = :leaveId
            """)
    Optional<LeaveManagement> findByIdForUpdate(
            @Param("leaveId") Long leaveId);

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployeeEmployeeId(Long employeeId);
}
