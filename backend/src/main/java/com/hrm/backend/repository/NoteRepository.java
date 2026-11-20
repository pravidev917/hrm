package com.hrm.backend.repository;

import com.hrm.backend.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByEmployeeEmployeeIdOrderByUpdatedAtDesc(Long employeeId);

    List<Note> findByAdminAdminIdOrderByUpdatedAtDesc(Long adminId);

    long countByAdminAdminId(Long adminId);

    Optional<Note> findByNoteIdAndEmployeeEmployeeId(Long noteId, Long employeeId);

    Optional<Note> findByNoteIdAndAdminAdminId(Long noteId, Long adminId);

    /**
     * Notes written by the company's employees inside a period, used by the monthly
     * report. Administrator-owned notes stay private to their author.
     */
    @Query("""
            SELECT n FROM Note n
            WHERE n.employee.admin.company.companyId =
                  (SELECT adm.company.companyId FROM Admin adm WHERE adm.adminId = :adminId)
              AND n.createdAt BETWEEN :from AND :to
            """)
    List<Note> findByEmployeeInCompanyOfAndCreatedAtBetween(
            @Param("adminId") Long adminId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployeeEmployeeId(Long employeeId);
}
