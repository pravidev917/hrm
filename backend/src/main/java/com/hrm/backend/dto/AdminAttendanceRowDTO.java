package com.hrm.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the administrator's attendance monitor: one employee on one date.
 *
 * {@code status} is derived from the session rows, never stored by hand:
 *   LOGGED_IN           a session is open right now
 *   LOGGED_OUT          sessions exist and all of them are closed
 *   NOT_LOGGED_IN       today (or a future date) with no session yet
 *   INCOMPLETE_SESSION  a session was left open and the day is over
 *   ABSENT              an earlier working day with no login and no approved leave
 *   ON_LEAVE            approved leave covers the date
 *   HOLIDAY / WEEKLY_OFF  non-working day from the company calendar
 *   PRESENT             a historical PRESENT record with no login session (legacy data)
 */
public record AdminAttendanceRowDTO(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String designation,
        LocalDate attendanceDate,
        Instant firstLoginAt,
        Instant lastLogoutAt,
        long totalWorkSeconds,
        int sessionCount,
        String status,
        boolean workingDay,
        String workMode,
        boolean wfhApproved,
        boolean onLeave,
        /** Session that is still open, if any; it is the one an administrator chases up. */
        Long openSessionId,
        Instant openSince,
        long openSeconds,
        /** The stored attendance record of that day, when one exists (legacy corrections). */
        Long attendanceRecordId) {
}
