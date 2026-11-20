package com.hrm.backend.repository;

import com.hrm.backend.entity.OfficeLocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OfficeLocationRepository extends JpaRepository<OfficeLocation, Long> {

    Optional<OfficeLocation> findByCompanyCompanyId(Long companyId);

    /** Office settings last edited by this administrator (audit attribution). */
    long countByUpdatedByAdminId(Long adminId);

    /** Serialises concurrent edits of one company's office settings. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OfficeLocation o WHERE o.company.companyId = :companyId")
    Optional<OfficeLocation> findByCompanyCompanyIdForUpdate(@Param("companyId") Long companyId);
}
