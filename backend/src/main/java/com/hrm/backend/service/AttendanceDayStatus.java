package com.hrm.backend.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * The attendance vocabulary and the one place a day's status is decided.
 *
 * A day is described by evidence, in this order:
 *   1. an open session left behind on an earlier day   -> INCOMPLETE_SESSION
 *   2. an open session dated today                      -> LOGGED_IN
 *   3. sessions that were opened and closed             -> LOGGED_OUT
 *   4. a company holiday                                -> HOLIDAY
 *   5. a Sunday (Saturday is a working day)             -> WEEKLY_OFF
 *   6. approved leave covering the date                 -> ON_LEAVE
 *   7. a stored PRESENT record without sessions         -> PRESENT (legacy rows)
 *   8. today or a future date with nothing yet          -> NOT_LOGGED_IN
 *   9. any other past working day                       -> ABSENT (derived, never written)
 *
 * Two rules matter more than the order itself: an ABSENT is only ever derived for a day
 * that is already over, and a session is never closed with a logout time nobody recorded.
 */
public final class AttendanceDayStatus {

    public static final String LOGGED_IN = "LOGGED_IN";
    public static final String LOGGED_OUT = "LOGGED_OUT";
    public static final String NOT_LOGGED_IN = "NOT_LOGGED_IN";
    public static final String INCOMPLETE_SESSION = "INCOMPLETE_SESSION";
    public static final String ABSENT = "ABSENT";
    public static final String ON_LEAVE = "ON_LEAVE";
    public static final String HOLIDAY = "HOLIDAY";
    public static final String WEEKLY_OFF = "WEEKLY_OFF";
    public static final String PRESENT = "PRESENT";

    private AttendanceDayStatus() {
    }

    /**
     * Monday to Saturday are working days; only Sunday and company holidays are not.
     * Weekly hours and absence are judged against this, so a Saturday login is a
     * normal working day and never counted as overtime or absence.
     */
    public static boolean isWorkingDay(LocalDate date, Set<LocalDate> holidays) {
        if (date == null) {
            return false;
        }
        return date.getDayOfWeek() != DayOfWeek.SUNDAY
                && (holidays == null || !holidays.contains(date));
    }

    public static boolean isWeeklyOff(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public static String resolve(
            LocalDate date,
            LocalDate today,
            Set<LocalDate> holidays,
            boolean hasSession,
            boolean hasOpenSession,
            boolean hasOpenSessionFromEarlierDay,
            boolean hasIncompleteSession,
            boolean onLeave,
            boolean legacyPresent) {

        if (hasOpenSessionFromEarlierDay || hasIncompleteSession) {
            return INCOMPLETE_SESSION;
        }
        if (hasOpenSession) {
            return LOGGED_IN;
        }
        if (hasSession) {
            return LOGGED_OUT;
        }
        if (holidays != null && holidays.contains(date)) {
            return HOLIDAY;
        }
        if (isWeeklyOff(date)) {
            return WEEKLY_OFF;
        }
        if (onLeave) {
            return ON_LEAVE;
        }
        if (legacyPresent) {
            return PRESENT;
        }
        if (!date.isBefore(today)) {
            // The day is not over: an absence cannot be claimed yet.
            return NOT_LOGGED_IN;
        }
        return ABSENT;
    }
}
