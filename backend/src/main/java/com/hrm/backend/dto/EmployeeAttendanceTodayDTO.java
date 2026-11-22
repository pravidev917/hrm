package com.hrm.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The employee's own attendance for the company-local today.
 *
 * Everything is derived server-side from the session rows of that date:
 * first login, last logout, the sum of completed sessions and how long the open
 * session has been running ({@code activeSeconds}, measured at response time).
 * The client only adds the seconds that pass after the response arrived.
 */
public record EmployeeAttendanceTodayDTO(
        Long employeeId,
        String employeeName,
        String employeeCode,
        LocalDate date,
        /** LOGGED_IN | LOGGED_OUT | NOT_LOGGED_IN | INCOMPLETE_SESSION | ON_LEAVE | HOLIDAY | WEEKLY_OFF */
        String status,
        /** True while a session of this employee is open. */
        boolean loggedIn,
        /** Approved work-from-home covering today; login then records WFH instead of OFFICE. */
        boolean wfhApproved,
        boolean onLeave,
        boolean workingDay,
        /** OFFICE or WFH for today's attendance record, null before the first login. */
        String workMode,
        Instant firstLoginAt,
        Instant lastLogoutAt,
        /** Sum of the completed sessions of today. */
        long totalWorkSeconds,
        /** Seconds of the currently open session, measured now; 0 when logged out. */
        long activeSeconds,
        /** Start of the open session, so the client can keep counting; null when logged out. */
        Instant activeSince,
        int sessionCount,
        List<AttendanceSessionDTO> sessions,
        /** Server message worth showing once, e.g. a session left open on an earlier day. */
        String notice) {
}
