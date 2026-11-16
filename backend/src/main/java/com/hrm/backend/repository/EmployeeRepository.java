package com.hrm.backend.repository;

import com.hrm.backend.entity.Employee;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByAdminAdminId(Long adminId);

    List<Employee> findByRole(String role);

    boolean existsByEmail(String email);

    Optional<Employee> findByEmail(String email);

    /**
     * Admin-scoped employee search with optional name/email/designation term and status.
     * Organisation scoping is enforced in SQL (admin_id), not in the browser.
     */
    @Query("""
            SELECT e FROM Employee e
            WHERE e.admin.adminId = :adminId
              AND (:status IS NULL OR e.accountStatus = :status)
              AND (:term IS NULL
                   OR LOWER(e.employeeName) LIKE :term
                   OR LOWER(e.email) LIKE :term
                   OR LOWER(e.role) LIKE :term
                   OR LOWER(COALESCE(e.contact, '')) LIKE :term)
            """)
    Page<Employee> searchByAdmin(
            @Param("adminId") Long adminId,
            @Param("term") String term,
            @Param("status") String status,
            Pageable pageable
    );

    long countByAdminAdminId(Long adminId);

    long countByAdminAdminIdAndAccountStatus(Long adminId, String accountStatus);

    // Lock employee row while validating and saving leave applications.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.employeeId = :employeeId")
    Optional<Employee> findByIdForUpdate(
            @Param("employeeId") Long employeeId
    );
}
