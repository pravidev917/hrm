package com.hrm.backend.repository;

import com.hrm.backend.entity.DailyReportPlatformCount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Per-platform rows of a daily report.
 *
 * Editing a report replaces its platform rows. Because {@code (daily_report_id, platform)}
 * is unique, the old rows have to be deleted in the database <em>before</em> the new ones
 * are inserted - clearing the entity collection alone lets Hibernate insert first and hit
 * the constraint. This repository is that explicit delete.
 */
public interface DailyReportPlatformCountRepository
        extends JpaRepository<DailyReportPlatformCount, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM DailyReportPlatformCount pc
            WHERE pc.dailyReport.dailyReportId = :reportId
            """)
    int deleteByReportId(@Param("reportId") Long reportId);
}
