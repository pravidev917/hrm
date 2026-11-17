
package com.hrm.backend.repository;

import com.hrm.backend.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // Employee: retrieve own feedback
    List<Feedback> findByEmployee_EmployeeIdOrderByFeedbackIdDesc(
            Long employeeId
    );

    Optional<Feedback> findByFeedbackIdAndEmployee_EmployeeId(
            Long feedbackId,
            Long employeeId
    );

    // Admin: retrieve feedback belonging to their employees
    List<Feedback> findByEmployee_Admin_AdminIdOrderByFeedbackIdDesc(
            Long adminId
    );

    Optional<Feedback> findByFeedbackIdAndEmployee_Admin_AdminId(
            Long feedbackId,
            Long adminId
    );

    // Cascade cleanup when an administrator deletes an employee.
    long deleteByEmployee_EmployeeId(Long employeeId);
}
