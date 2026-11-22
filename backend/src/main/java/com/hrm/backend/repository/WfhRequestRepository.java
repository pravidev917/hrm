package com.hrm.backend.repository;

import com.hrm.backend.entity.WfhRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WfhRequestRepository extends JpaRepository<WfhRequest, Long> {

    List<WfhRequest> findByEmployeeEmployeeIdOrderByStartDateDesc(Long employeeId);

    /** Whole-company queue (shared by every administrator of the company). */
    @Query("""
            SELECT w FROM WfhRequest w
            WHERE w.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY w.startDate DESC, w.wfhId DESC
            """)
    List<WfhRequest> findByEmployeeInCompanyOf(@Param("adminId") Long adminId);

    /**
     * The approved request covering one company-local date, if any.
     * Only APPROVED status is considered: pending, rejected, cancelled, expired
     * and future requests never bypass office verification.
     */
    @Query("""
            SELECT w FROM WfhRequest w
            WHERE w.employee.employeeId = :employeeId
              AND w.status = 'APPROVED'
              AND w.startDate <= :date
              AND w.endDate >= :date
            ORDER BY w.startDate ASC
            """)
    List<WfhRequest> findApprovedCovering(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date);

    /** Overlap check for active requests of one employee. */
    @Query("""
            SELECT w FROM WfhRequest w
            WHERE w.employee.employeeId = :employeeId
              AND w.status IN :statuses
              AND w.startDate <= :endDate
              AND w.endDate >= :startDate
            """)
    List<WfhRequest> findOverlapping(
            @Param("employeeId") Long employeeId,
            @Param("statuses") List<String> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** Approved WFH of the whole company that touches the range, used by attendance views. */
    @Query("""
            SELECT w FROM WfhRequest w
            WHERE w.status = 'APPROVED'
              AND w.startDate <= :endDate
              AND w.endDate >= :startDate
              AND w.employee.admin.company.companyId =
                    (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    List<WfhRequest> findApprovedInCompanyOfBetween(
            @Param("adminId") Long adminId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WfhRequest w WHERE w.wfhId = :wfhId")
    Optional<WfhRequest> findByIdForUpdate(@Param("wfhId") Long wfhId);

    long countByEmployeeEmployeeIdAndStatus(Long employeeId, String status);

    @Query("""
            SELECT COUNT(w) FROM WfhRequest w
            WHERE w.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND w.status = :status
            """)
    long countInCompanyOfAndStatus(
            @Param("adminId") Long adminId,
            @Param("status") String status);

    /** WFH decisions made by this administrator (audit trail). */
    long countByReviewedByAdminId(Long adminId);

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployeeEmployeeId(Long employeeId);
}
