package com.hrm.backend.repository;

import com.hrm.backend.entity.AttendanceSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Attendance sessions, always read with the employee joined.
 *
 * The application runs with open-in-view disabled and controllers map entities after
 * the transaction has closed, so every query that feeds a response fetches the
 * employee (and through it the administrator/company) in the same query.
 */
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    // ================= EMPLOYEE =================

    Optional<AttendanceSession> findByEmployeeEmployeeIdAndStatus(Long employeeId, String status);

    @Query("""
            SELECT s FROM AttendanceSession s
            JOIN FETCH s.employee e
            WHERE e.employeeId = :employeeId AND s.status = :status
            """)
    Optional<AttendanceSession> findOneByEmployeeAndStatus(
            @Param("employeeId") Long employeeId,
            @Param("status") String status);

    @Query("""
            SELECT s FROM AttendanceSession s
            JOIN FETCH s.employee e
            WHERE e.employeeId = :employeeId
              AND s.attendanceDate BETWEEN :from AND :to
            ORDER BY s.attendanceDate DESC, s.loginAt ASC
            """)
    List<AttendanceSession> findForEmployeeBetween(
            @Param("employeeId") Long employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT s FROM AttendanceSession s
            JOIN FETCH s.employee e
            WHERE e.employeeId = :employeeId AND s.attendanceDate = :date
            ORDER BY s.loginAt ASC
            """)
    List<AttendanceSession> findForEmployeeOnDate(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date);

    // ================= ADMIN (whole company) =================

    @Query("""
            SELECT s FROM AttendanceSession s
            JOIN FETCH s.employee e
            WHERE s.status = 'ACTIVE'
              AND e.admin.company.companyId =
                    (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY s.loginAt ASC
            """)
    List<AttendanceSession> findActiveInCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT s FROM AttendanceSession s
            JOIN FETCH s.employee e
            WHERE s.attendanceDate BETWEEN :from AND :to
              AND e.admin.company.companyId =
                    (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY s.attendanceDate DESC, s.loginAt ASC
            """)
    List<AttendanceSession> findInCompanyOfBetween(
            @Param("adminId") Long adminId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // ================= MAINTENANCE =================

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AttendanceSession s WHERE s.sessionId = :sessionId")
    Optional<AttendanceSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployeeEmployeeId(Long employeeId);
}
