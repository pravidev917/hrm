package com.hrm.backend.repository;

import com.hrm.backend.entity.Admin;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Email uniqueness check for an edit: every other administrator, excluding this row. */
    boolean existsByEmailAndAdminIdNot(String email, Long adminId);

    long countByStatus(String status);

    /** Active accounts inside one company: the "last administrator" guard is per company. */
    long countByCompanyCompanyIdAndStatus(Long companyId, String status);

    /** The company of an administrator. Resolved from the database, never from a request. */
    @Query("select a.company.companyId from Admin a where a.adminId = :adminId")
    Optional<Long> findCompanyIdByAdminId(@Param("adminId") Long adminId);

    /** Every administrator of the caller's company. */
    List<Admin> findByCompanyCompanyIdOrderByAdminIdAsc(Long companyId);

    /**
     * Locks the administrator rows of one company for the rest of the transaction so
     * two concurrent deactivations cannot each believe another active administrator
     * remains in that company.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a where a.company.companyId = :companyId order by a.adminId")
    List<Admin> findCompanyForUpdate(@Param("companyId") Long companyId);

    /**
     * Locks every administrator row for the rest of the transaction.
     *
     * Account status changes read the number of active administrators from this
     * locked snapshot, so two concurrent deactivations cannot each believe that
     * another active administrator remains. The table holds a handful of rows, so
     * the coarse lock is cheap and it keeps the rule safe without a read-then-write
     * race.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a order by a.adminId")
    List<Admin> findAllForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a where a.adminId = :adminId")
    Optional<Admin> findByIdForUpdate(@Param("adminId") Long adminId);
}
