package com.hrm.backend.repository;

import com.hrm.backend.entity.Attendance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Employee-scoped queries are per employee; administrator queries are scoped to
 * the company of the administrator (shared records), not to the creator, so every
 * administrator of a company sees the same attendance.
 */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    // EMPLOYEE
    List<Attendance> findByEmployeeEmployeeId(Long employeeId);

    Optional<Attendance> findByEmployeeEmployeeIdAndAttendanceDate(
            Long employeeId,
            LocalDate attendanceDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Attendance a WHERE a.attendanceId = :attendanceId")
    Optional<Attendance> findByIdForUpdate(@Param("attendanceId") Long attendanceId);

    List<Attendance> findByEmployeeEmployeeIdAndAttendanceDateBetween(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate);

    // GENERAL DATE QUERIES
    List<Attendance> findByAttendanceDate(LocalDate attendanceDate);

    List<Attendance> findByAttendanceDateBetween(
            LocalDate startDate,
            LocalDate endDate);

    // ADMIN - ALL EMPLOYEES OF THE ADMINISTRATOR'S COMPANY
    @Query("""
            SELECT a FROM Attendance a
            WHERE a.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    List<Attendance> findByEmployeeInCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT a FROM Attendance a
            WHERE a.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND a.attendanceDate BETWEEN :startDate AND :endDate
            """)
    List<Attendance> findByEmployeeInCompanyOfAndAttendanceDateBetween(
            @Param("adminId") Long adminId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // EMPLOYEE MONTHLY DASHBOARD STATISTICS
    long countByEmployeeEmployeeIdAndAttendanceDateBetweenAndStatus(
            Long employeeId, LocalDate startDate, LocalDate endDate, String status);

    long countByEmployeeEmployeeIdAndAttendanceDateBetween(
            Long employeeId, LocalDate startDate, LocalDate endDate);

    // STATISTICS
    long countByEmployeeEmployeeIdAndStatus(
            Long employeeId,
            String status);

    // ADMIN DASHBOARD: attendance across the company's employees
    @Query("""
            SELECT COUNT(a) FROM Attendance a
            WHERE a.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND a.attendanceDate = :attendanceDate
              AND a.status = :status
            """)
    long countByEmployeeInCompanyOfAndAttendanceDateAndStatus(
            @Param("adminId") Long adminId,
            @Param("attendanceDate") LocalDate attendanceDate,
            @Param("status") String status);

    @Query("""
            SELECT COUNT(a) FROM Attendance a
            WHERE a.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND a.attendanceDate = :attendanceDate
            """)
    long countByEmployeeInCompanyOfAndAttendanceDate(
            @Param("adminId") Long adminId,
            @Param("attendanceDate") LocalDate attendanceDate);

    @Query("""
            SELECT COUNT(a) FROM Attendance a
            WHERE a.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND a.attendanceDate BETWEEN :startDate AND :endDate
            """)
    long countByEmployeeInCompanyOfAndAttendanceDateBetween(
            @Param("adminId") Long adminId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployeeEmployeeId(Long employeeId);
}
