package com.hrm.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One login/logout pair, as shown to the employee and to the administrator.
 *
 * Times are UTC instants (ISO-8601 with an offset) so the browser can render them in
 * the company timezone without guessing. {@code durationSeconds} is null while the
 * session is still open; the client shows a live counter from {@code loginAt} instead
 * of a number the server would have to keep refreshing.
 */
public record AttendanceSessionDTO(
        Long sessionId,
        Long employeeId,
        String employeeName,
        LocalDate attendanceDate,
        Instant loginAt,
        Instant logoutAt,
        Long durationSeconds,
        String status,
        boolean active) {
}
