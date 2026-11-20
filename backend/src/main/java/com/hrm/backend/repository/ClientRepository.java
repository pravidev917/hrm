package com.hrm.backend.repository;

import com.hrm.backend.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Clients belong to the administrator's company: every administrator of that
 * company sees and edits the same client list. `admin_id` stays the creator.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

    @Query("""
            SELECT c FROM Client c
            WHERE c.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY c.clientName ASC
            """)
    List<Client> findByCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT COUNT(c) FROM Client c
            WHERE c.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    long countByCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT c FROM Client c
            WHERE c.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND c.status = :status
            ORDER BY c.clientName ASC
            """)
    List<Client> findByCompanyOfAndStatus(
            @Param("adminId") Long adminId,
            @Param("status") String status);

    List<Client> findByClientNameContainingIgnoreCase(String clientName);

    /** Client codes are unique within a company, whoever created them. */
    @Query("""
            SELECT COUNT(c) > 0 FROM Client c
            WHERE c.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND LOWER(c.clientCode) = LOWER(:clientCode)
            """)
    boolean existsClientCodeInCompanyOf(
            @Param("adminId") Long adminId,
            @Param("clientCode") String clientCode);

    @Query("""
            SELECT c FROM Client c
            WHERE c.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND LOWER(c.clientCode) = LOWER(:clientCode)
            """)
    Optional<Client> findClientCodeInCompanyOf(
            @Param("adminId") Long adminId,
            @Param("clientCode") String clientCode);
}
