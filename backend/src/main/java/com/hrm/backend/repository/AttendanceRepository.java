package com.hrm.backend.repository;

import com.hrm.backend.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    // EMPLOYEE
    List<Attendance> findByEmployeeEmployeeId(Long employeeId);

    Optional<Attendance> findByEmployeeEmployeeIdAndAttendanceDate(
            Long employeeId,
            LocalDate attendanceDate);

    List<Attendance> findByEmployeeEmployeeIdAndAttendanceDateBetween(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate);

    // GENERAL DATE QUERIES
    List<Attendance> findByAttendanceDate(LocalDate attendanceDate);

    List<Attendance> findByAttendanceDateBetween(
            LocalDate startDate,
            LocalDate endDate);

    // ADMIN - ALL EMPLOYEES UNDER ADMIN
    List<Attendance> findByEmployeeAdminAdminId(Long adminId);

    List<Attendance> findByEmployeeAdminAdminIdAndAttendanceDateBetween(
            Long adminId,
            LocalDate startDate,
            LocalDate endDate);

    // EMPLOYEE MONTHLY DASHBOARD STATISTICS
    long countByEmployeeEmployeeIdAndAttendanceDateBetweenAndStatus(
            Long employeeId, LocalDate startDate, LocalDate endDate, String status);

    long countByEmployeeEmployeeIdAndAttendanceDateBetween(
            Long employeeId, LocalDate startDate, LocalDate endDate);

    // STATISTICS
    long countByEmployeeEmployeeIdAndStatus(
            Long employeeId,
            String status);

    // ADMIN DASHBOARD: attendance across one admin's employees
    long countByEmployeeAdminAdminIdAndAttendanceDateAndStatus(
            Long adminId, LocalDate attendanceDate, String status);

    long countByEmployeeAdminAdminIdAndAttendanceDate(
            Long adminId, LocalDate attendanceDate);

    long countByEmployeeAdminAdminIdAndAttendanceDateBetween(
            Long adminId, LocalDate startDate, LocalDate endDate);
}