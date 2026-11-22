package com.hrm.backend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Working-day and status rules, exercised without a database.
 *
 * Saturday is a working day (only Sunday and company holidays are not), and an absence
 * can only be derived once the day is over.
 */
class AttendanceDayStatusTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 21);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 22);
    private static final Set<LocalDate> NO_HOLIDAYS = Set.of();

    @Test
    void saturdayIsAWorkingDayAndSundayIsNot() {
        assertTrue(AttendanceDayStatus.isWorkingDay(MONDAY, NO_HOLIDAYS));
        assertTrue(AttendanceDayStatus.isWorkingDay(SATURDAY, NO_HOLIDAYS),
                "Saturday must count as a working day");
        assertFalse(AttendanceDayStatus.isWorkingDay(SUNDAY, NO_HOLIDAYS));
        assertTrue(AttendanceDayStatus.isWeeklyOff(SUNDAY));
    }

    @Test
    void aCompanyHolidayIsNotAWorkingDayEvenOnAWeekday() {
        assertFalse(AttendanceDayStatus.isWorkingDay(MONDAY, Set.of(MONDAY)));
    }

    @Test
    void anOpenSessionMeansLoggedIn() {
        assertEquals(AttendanceDayStatus.LOGGED_IN, AttendanceDayStatus.resolve(
                SATURDAY, SATURDAY, NO_HOLIDAYS,
                true, true, false, false, false, false));
    }

    @Test
    void closedSessionsMeanLoggedOut() {
        assertEquals(AttendanceDayStatus.LOGGED_OUT, AttendanceDayStatus.resolve(
                SATURDAY, SATURDAY, NO_HOLIDAYS,
                true, false, false, false, false, false));
    }

    @Test
    void aSessionLeftOpenOnAnEarlierDayIsIncomplete() {
        assertEquals(AttendanceDayStatus.INCOMPLETE_SESSION, AttendanceDayStatus.resolve(
                SATURDAY, SUNDAY, NO_HOLIDAYS,
                true, true, true, false, false, false));
    }

    @Test
    void todayWithoutALoginIsNotLoggedInYet_notAbsent() {
        assertEquals(AttendanceDayStatus.NOT_LOGGED_IN, AttendanceDayStatus.resolve(
                SATURDAY, SATURDAY, NO_HOLIDAYS,
                false, false, false, false, false, false));
    }

    @Test
    void aPastWorkingDayWithoutAnyEvidenceIsAbsent() {
        assertEquals(AttendanceDayStatus.ABSENT, AttendanceDayStatus.resolve(
                MONDAY, SATURDAY, NO_HOLIDAYS,
                false, false, false, false, false, false));
    }

    @Test
    void approvedLeaveWinsOverAbsence() {
        assertEquals(AttendanceDayStatus.ON_LEAVE, AttendanceDayStatus.resolve(
                MONDAY, SATURDAY, NO_HOLIDAYS,
                false, false, false, false, true, false));
    }

    @Test
    void aSundayIsAWeeklyOffNotAnAbsence() {
        assertEquals(AttendanceDayStatus.WEEKLY_OFF, AttendanceDayStatus.resolve(
                SUNDAY, SATURDAY, NO_HOLIDAYS,
                false, false, false, false, false, false));
    }

    @Test
    void aHistoricalPresentRecordStaysPresent() {
        assertEquals(AttendanceDayStatus.PRESENT, AttendanceDayStatus.resolve(
                MONDAY, SATURDAY, NO_HOLIDAYS,
                false, false, false, false, false, true));
    }
}
