package com.hrm.backend.repository;

import com.hrm.backend.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Holidays are shared by every administrator of the company. */
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    @Query("""
            SELECT h FROM Holiday h
            WHERE h.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            ORDER BY h.holidayDate ASC
            """)
    List<Holiday> findByCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT COUNT(h) FROM Holiday h
            WHERE h.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
            """)
    long countByCompanyOf(@Param("adminId") Long adminId);

    @Query("""
            SELECT h FROM Holiday h
            WHERE h.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND h.holidayDate BETWEEN :startDate AND :endDate
            ORDER BY h.holidayDate ASC
            """)
    List<Holiday> findByCompanyOfAndHolidayDateBetween(
            @Param("adminId") Long adminId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /** Records created by one administrator, used by the account-deletion guard. */
    long countByAdminAdminId(Long adminId);
}
