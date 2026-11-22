package com.hrm.backend.service;

import com.hrm.backend.entity.Attendance;
import com.hrm.backend.entity.AttendanceSession;
import com.hrm.backend.entity.LeaveManagement;
import com.hrm.backend.entity.WfhRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory index over the attendance evidence of a date range.
 *
 * The administrator views need one row per employee per day and the employee view needs
 * one day; both are answered from the same loaded rows, so a page never runs a query per
 * employee per day and two views can never disagree about the same day.
 *
 * Nothing here writes: a day that was never recorded simply reports the status the rules
 * derive (absence for a past working day, weekly off, holiday, ...).
 */
final class AttendanceLedger {

    private final Map<Long, Map<LocalDate, List<AttendanceSession>>> sessions = new HashMap<>();
    private final Map<Long, Map<LocalDate, Attendance>> records = new HashMap<>();
    private final Map<Long, Set<LocalDate>> leaveDays = new HashMap<>();
    private final Map<Long, Set<LocalDate>> wfhDays = new HashMap<>();
    private final Set<LocalDate> holidays = new HashSet<>();
    private final LocalDate today;
    private final Instant now;

    AttendanceLedger(
            Collection<AttendanceSession> sessionRows,
            Collection<Attendance> recordRows,
            Collection<LeaveManagement> leaves,
            Collection<WfhRequest> wfhRequests,
            Collection<LocalDate> holidayDates,
            LocalDate today,
            Instant now) {

        this.today = today;
        this.now = now;

        if (sessionRows != null) {
            for (AttendanceSession row : sessionRows) {
                if (row.getEmployee() == null || row.getAttendanceDate() == null) {
                    continue;
                }
                sessions
                        .computeIfAbsent(row.getEmployee().getEmployeeId(), id -> new HashMap<>())
                        .computeIfAbsent(row.getAttendanceDate(), date -> new ArrayList<>())
                        .add(row);
            }
        }

        if (recordRows != null) {
            for (Attendance record : recordRows) {
                if (record.getEmployee() == null || record.getAttendanceDate() == null) {
                    continue;
                }
                records
                        .computeIfAbsent(record.getEmployee().getEmployeeId(), id -> new HashMap<>())
                        .put(record.getAttendanceDate(), record);
            }
        }

        if (leaves != null) {
            for (LeaveManagement leave : leaves) {
                if (leave.getEmployee() == null || !"APPROVED".equalsIgnoreCase(leave.getStatus())) {
                    continue;
                }
                addRange(leaveDays, leave.getEmployee().getEmployeeId(),
                        leave.getStartDate(), leave.getEndDate());
            }
        }

        if (wfhRequests != null) {
            for (WfhRequest wfh : wfhRequests) {
                if (wfh.getEmployee() == null || !"APPROVED".equalsIgnoreCase(wfh.getStatus())) {
                    continue;
                }
                addRange(wfhDays, wfh.getEmployee().getEmployeeId(),
                        wfh.getStartDate(), wfh.getEndDate());
            }
        }

        if (holidayDates != null) {
            holidays.addAll(holidayDates);
        }
    }

    Set<LocalDate> holidays() {
        return holidays;
    }

    boolean isWorkingDay(LocalDate date) {
        return AttendanceDayStatus.isWorkingDay(date, holidays);
    }

    /** Everything the rules and the DTOs need about one employee on one date. */
    DayFacts day(Long employeeId, LocalDate date) {

        List<AttendanceSession> rows = sessions
                .getOrDefault(employeeId, Map.of())
                .getOrDefault(date, List.of());

        Attendance record = records
                .getOrDefault(employeeId, Map.of())
                .get(date);

        boolean onLeave = leaveDays.getOrDefault(employeeId, Set.of()).contains(date);
        boolean wfhApproved = wfhDays.getOrDefault(employeeId, Set.of()).contains(date);

        // An open session from an earlier day can hide under *that* day's row, which is
        // exactly where it must be reported (and where the employee sees it in history).
        AttendanceSession open = null;
        boolean openFromEarlierDay = false;
        boolean incomplete = false;

        Instant firstLogin = null;
        Instant lastLogout = null;
        Instant openSince = null;
        long totalSeconds = 0;
        int sessionCount = 0;
        String workMode = record == null ? null : record.getWorkMode();

        for (AttendanceSession row : rows) {
            sessionCount++;
            if (firstLogin == null || row.getLoginAt().isBefore(firstLogin)) {
                firstLogin = row.getLoginAt();
            }
            if (row.getLogoutAt() != null && (lastLogout == null || row.getLogoutAt().isAfter(lastLogout))) {
                lastLogout = row.getLogoutAt();
            }
            if (row.getDurationSeconds() != null) {
                totalSeconds += Math.max(0L, row.getDurationSeconds());
            }
            if (AttendanceSession.STATUS_INCOMPLETE.equals(row.getStatus())) {
                incomplete = true;
            }
            if (row.isActive()) {
                open = row;
                openSince = row.getLoginAt();
                openFromEarlierDay = row.getAttendanceDate().isBefore(today);
            }
        }

        Set<LocalDate> companyHolidays = holidays;
        String status = AttendanceDayStatus.resolve(
                date,
                today,
                companyHolidays,
                sessionCount > 0,
                open != null && !openFromEarlierDay,
                openFromEarlierDay,
                incomplete,
                onLeave,
                record != null && "PRESENT".equalsIgnoreCase(record.getStatus()));

        // A session left open on an earlier day has no measurable duration: the employee
        // did not work for days, so counting the elapsed time would invent hours.
        long openSeconds = (openSince == null || openFromEarlierDay)
                ? 0L
                : Math.max(0L, Duration.between(openSince, now).getSeconds());

        return new DayFacts(
                date,
                status,
                isWorkingDay(date),
                firstLogin,
                lastLogout,
                totalSeconds,
                sessionCount,
                workMode,
                onLeave,
                wfhApproved,
                open == null ? null : open.getSessionId(),
                openSince,
                openSeconds,
                record == null ? null : record.getAttendanceId(),
                List.copyOf(rows));
    }

    private static void addRange(
            Map<Long, Set<LocalDate>> target,
            Long employeeId,
            LocalDate start,
            LocalDate end) {

        if (employeeId == null || start == null || end == null || start.isAfter(end)) {
            return;
        }

        // Guard against a mistyped request spanning years.
        LocalDate cursor = start;
        int guard = 0;
        Set<LocalDate> days = target.computeIfAbsent(employeeId, id -> new HashSet<>());
        while (!cursor.isAfter(end) && guard++ < 1500) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }
    }

    /** One employee on one date, already resolved into the vocabulary the UI uses. */
    record DayFacts(
            LocalDate date,
            String status,
            boolean workingDay,
            Instant firstLoginAt,
            Instant lastLogoutAt,
            long totalWorkSeconds,
            int sessionCount,
            String workMode,
            boolean onLeave,
            boolean wfhApproved,
            Long openSessionId,
            Instant openSince,
            long openSeconds,
            Long recordId,
            List<AttendanceSession> sessions) {
    }
}
