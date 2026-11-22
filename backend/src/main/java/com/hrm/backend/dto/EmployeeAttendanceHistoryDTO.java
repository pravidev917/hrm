package com.hrm.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One employee's day-by-day attendance history with every login/logout pair.
 *
 * Days without any session and without a stored attendance row are still reported so
 * the history adds up: a past working day with no login is ABSENT, a Sunday is
 * WEEKLY_OFF and a company holiday is HOLIDAY. Nothing is invented - a day the
 * employee left a session open is INCOMPLETE_SESSION with a null logout time.
 */
public record EmployeeAttendanceHistoryDTO(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String designation,
        LocalDate from,
        LocalDate to,
        long totalWorkSeconds,
        long sessionCount,
        long daysWorked,
        long incompleteSessions,
        List<Day> days) {

    public record Day(
            LocalDate date,
            String status,
            boolean workingDay,
            Instant firstLoginAt,
            Instant lastLogoutAt,
            long totalWorkSeconds,
            int sessionCount,
            String workMode,
            List<AttendanceSessionDTO> sessions) {
    }
}
