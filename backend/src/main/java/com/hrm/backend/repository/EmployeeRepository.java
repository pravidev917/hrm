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

    /**
     * Every employee of the administrator's company: employees are shared with all
     * administrators of that company, whoever created the account.
     */
    @Query("""
            SELECT e FROM Employee e
            WHERE e.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY e.employeeName ASC
            """)
    List<Employee> findByCompanyOf(@Param("adminId") Long adminId);

    List<Employee> findByRole(String role);

    boolean existsByEmail(String email);

    Optional<Employee> findByEmail(String email);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    Optional<Employee> findByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByContact(String contact);

    Optional<Employee> findByContact(String contact);

    /**
     * Company-scoped employee search with optional term and status.
     * The company comes from the authenticated administrator's row, not from the
     * request, so records are shared inside a company and isolated between them.
     */
    @Query("""
            SELECT e FROM Employee e
            WHERE e.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND (:status IS NULL OR e.accountStatus = :status)
              AND (:term IS NULL
                   OR LOWER(e.employeeName) LIKE :term
                   OR LOWER(e.email) LIKE :term
                   OR LOWER(e.role) LIKE :term
                   OR LOWER(COALESCE(e.department, '')) LIKE :term
                   OR LOWER(COALESCE(e.contact, '')) LIKE :term
                   OR LOWER(COALESCE(e.employeeCode, '')) LIKE :term)
            """)
    Page<Employee> searchInCompanyOf(
            @Param("adminId") Long adminId,
            @Param("term") String term,
            @Param("status") String status,
            Pageable pageable
    );

    /** Accounts this administrator created, used by the account-deletion guard. */
    long countByAdminAdminId(Long adminId);

    long countByAdminAdminIdAndAccountStatus(Long adminId, String accountStatus);

    /** Everyone in the administrator's company, whoever created the account. */
    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    long countInCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND e.accountStatus = :accountStatus
            """)
    long countInCompanyOfAndAccountStatus(
            @Param("adminId") Long adminId,
            @Param("accountStatus") String accountStatus);

    // Lock employee row while validating and saving leave applications.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.employeeId = :employeeId")
    Optional<Employee> findByIdForUpdate(
            @Param("employeeId") Long employeeId
    );
}
