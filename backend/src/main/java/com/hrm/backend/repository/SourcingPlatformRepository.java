package com.hrm.backend.repository;

import com.hrm.backend.entity.SourcingPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SourcingPlatformRepository extends JpaRepository<SourcingPlatform, Long> {

    @Query("""
            SELECT p FROM SourcingPlatform p
            WHERE p.company.companyId = :companyId
            ORDER BY p.sortOrder ASC, lower(p.name) ASC
            """)
    List<SourcingPlatform> findByCompany(@Param("companyId") Long companyId);

    @Query("""
            SELECT p FROM SourcingPlatform p
            WHERE p.sourcingPlatformId = :id
              AND p.company.companyId = :companyId
            """)
    Optional<SourcingPlatform> findByIdInCompany(
            @Param("id") Long id,
            @Param("companyId") Long companyId);

    @Query("""
            SELECT p FROM SourcingPlatform p
            WHERE p.company.companyId = :companyId
              AND lower(p.name) = lower(:name)
            """)
    Optional<SourcingPlatform> findByCompanyAndName(
            @Param("companyId") Long companyId,
            @Param("name") String name);
}
