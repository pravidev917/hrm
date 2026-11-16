package com.hrm.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the calendar's side panel shows for one month.
 *
 * @param month       first day of the month these events describe
 * @param events      birthdays, work anniversaries and holidays, soonest first
 * @param achievements resume/submission totals per employee for the month
 */
public record CalendarEventsDTO(
        LocalDate month,
        LocalDate today,
        List<CalendarEventDTO> events,
        List<AchievementDTO> achievements
) {
    /**
     * @param type          BIRTHDAY | ANNIVERSARY | HOLIDAY
     * @param title         what happened ("Rahul Kumar's birthday", "Diwali")
     * @param date          the date within the requested month
     * @param daysAway      days from today; negative means already passed
     * @param years         for ANNIVERSARY, which work anniversary; null otherwise
     * @param employeeName  set for BIRTHDAY / ANNIVERSARY, null for HOLIDAY
     */
    public record CalendarEventDTO(
            String type,
            String title,
            LocalDate date,
            long daysAway,
            Integer years,
            String employeeName,
            String description
    ) {
    }

    public record AchievementDTO(
            Long employeeId,
            String employeeName,
            String role,
            long resumesSubmitted,
            long candidatesSourced,
            long reportsFiled
    ) {
    }
}
