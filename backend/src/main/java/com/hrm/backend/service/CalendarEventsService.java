package com.hrm.backend.service;

import com.hrm.backend.dto.CalendarEventsDTO;
import com.hrm.backend.dto.CalendarEventsDTO.AchievementDTO;
import com.hrm.backend.dto.CalendarEventsDTO.CalendarEventDTO;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.Holiday;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the calendar side panel: birthdays, work anniversaries, holidays and
 * resume-count achievements for one month of one admin's team.
 *
 * Birthdays and anniversaries repeat every year, so they are matched on
 * month/day rather than on the stored year.
 */
@Service
public class CalendarEventsService {

    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final DailyReportRepository dailyReportRepository;

    public CalendarEventsService(
            EmployeeRepository employeeRepository,
            HolidayRepository holidayRepository,
            DailyReportRepository dailyReportRepository) {
        this.employeeRepository = employeeRepository;
        this.holidayRepository = holidayRepository;
        this.dailyReportRepository = dailyReportRepository;
    }

    @Transactional(readOnly = true)
    public CalendarEventsDTO getEvents(Long adminId, YearMonth month) {
        LocalDate today = LocalDate.now();
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<Employee> employees = employeeRepository.findByCompanyOf(adminId);
        List<CalendarEventDTO> events = new ArrayList<>();

        for (Employee employee : employees) {
            addAnnualEvent(events, employee, employee.getDateOfBirth(), "BIRTHDAY", today, start, end);
            addAnnualEvent(events, employee, employee.getJoiningDate(), "ANNIVERSARY", today, start, end);
        }

        for (Holiday holiday : holidayRepository
                .findByCompanyOfAndHolidayDateBetween(adminId, start, end)) {
            events.add(new CalendarEventDTO(
                    "HOLIDAY",
                    holiday.getHolidayName(),
                    holiday.getHolidayDate(),
                    ChronoUnit.DAYS.between(today, holiday.getHolidayDate()),
                    null,
                    null,
                    holiday.getDescription()
            ));
        }

        events.sort(Comparator.comparingLong(CalendarEventDTO::daysAway)
                .thenComparing(CalendarEventDTO::date)
                .thenComparing(CalendarEventDTO::title));

        return new CalendarEventsDTO(start, today, events, achievements(adminId, start, end, employees));
    }

    /**
     * Birthdays and anniversaries recur yearly. Only the month/day of the stored
     * date matters; Feb 29 is matched in non-leap years on Feb 28.
     */
    private void addAnnualEvent(
            List<CalendarEventDTO> events,
            Employee employee,
            LocalDate stored,
            String type,
            LocalDate today,
            LocalDate start,
            LocalDate end) {

        if (stored == null) return;

        LocalDate thisYear = safeDate(start.getYear(), stored.getMonthValue(), stored.getDayOfMonth());
        if (thisYear == null) return; // e.g. Feb 29 in a non-leap year

        if (thisYear.isBefore(start) || thisYear.isAfter(end)) return;

        Integer years = null;
        String title;

        if ("ANNIVERSARY".equals(type)) {
            int completed = start.getYear() - stored.getYear();
            if (completed < 1) return; // no "0th anniversary"
            years = completed;
            title = employee.getEmployeeName() + " · " + completed + yearSuffix(completed) + " work anniversary";
        } else {
            title = employee.getEmployeeName() + "'s birthday";
        }

        events.add(new CalendarEventDTO(
                type,
                title,
                thisYear,
                ChronoUnit.DAYS.between(today, thisYear),
                years,
                employee.getEmployeeName(),
                employee.getRole()
        ));
    }

    /**
     * Returns null when the day does not exist in that year (Feb 29). Falls back to
     * Feb 28 so a leap-day birthday still shows up every year.
     */
    private LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException e) {
            if (month == 2 && day == 29) return LocalDate.of(year, 2, 28);
            return null;
        }
    }

    private String yearSuffix(int years) {
        if (years % 100 >= 11 && years % 100 <= 13) return "th";
        return switch (years % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    /**
     * Resume ("profiles submitted") totals for the month, highest first.
     * Employees with no reports are omitted so the panel only shows real work.
     */
    private List<AchievementDTO> achievements(
            Long adminId, LocalDate start, LocalDate end, List<Employee> employees) {

        Map<Long, long[]> totals = new LinkedHashMap<>(); // employeeId -> [resumes, sourced, reports]

        for (DailyReport report : dailyReportRepository.findByEmployeeInCompanyOf(adminId)) {
            LocalDate date = report.getReportDate();
            if (date == null || date.isBefore(start) || date.isAfter(end)) continue;

            Long employeeId = report.getEmployee().getEmployeeId();
            long[] bucket = totals.computeIfAbsent(employeeId, key -> new long[3]);
            bucket[0] += report.getProfilesSubmitted() == null ? 0 : report.getProfilesSubmitted();
            bucket[1] += report.getCandidatesSourced() == null ? 0 : report.getCandidatesSourced();
            bucket[2] += 1;
        }

        List<AchievementDTO> result = new ArrayList<>();
        for (Employee employee : employees) {
            long[] bucket = totals.get(employee.getEmployeeId());
            if (bucket == null) continue;
            result.add(new AchievementDTO(
                    employee.getEmployeeId(),
                    employee.getEmployeeName(),
                    employee.getRole(),
                    bucket[0],
                    bucket[1],
                    bucket[2]
            ));
        }

        result.sort(Comparator.comparingLong(AchievementDTO::resumesSubmitted).reversed()
                .thenComparing(AchievementDTO::employeeName));
        return result;
    }
}
