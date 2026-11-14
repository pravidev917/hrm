package com.hrm.backend.repository;

import com.hrm.backend.entity.LeaveManagement;
import jakarta.persistence.LockModeType;
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

    List<LeaveManagement>
    findByEmployeeAdminAdminIdOrderByStartDateDesc(Long adminId);

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

    List<LeaveManagement>
    findByEmployeeAdminAdminIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long adminId,
            LocalDate endDate,
            LocalDate startDate);

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
}