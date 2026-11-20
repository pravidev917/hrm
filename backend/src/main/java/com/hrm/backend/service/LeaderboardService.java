package com.hrm.backend.service;

import com.hrm.backend.dto.LeaderboardDTO;
import com.hrm.backend.dto.LeaderboardDTO.LeaderboardRowDTO;
import com.hrm.backend.dto.LeaderboardDTO.RoleLeaderboardDTO;
import com.hrm.backend.entity.DailyReport;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ranks an admin's employees against each other, grouped by job designation.
 *
 * Ranking uses the daily-report numbers because they are the only comparable,
 * factual measurements the system holds. The manual "overall rating" in the
 * performance notes is not used for ordering: it is free-form, mostly empty, and
 * one person's 4.0 is not the same scale as another's.
 */
@Service
public class LeaderboardService {

    /** The metric ranks are based on; also echoed back to the client. */
    public static final String METRIC = "resumesSubmitted";

    private final EmployeeRepository employeeRepository;
    private final DailyReportRepository dailyReportRepository;

    public LeaderboardService(
            EmployeeRepository employeeRepository,
            DailyReportRepository dailyReportRepository) {
        this.employeeRepository = employeeRepository;
        this.dailyReportRepository = dailyReportRepository;
    }

    /**
     * @param viewerEmail the signed-in account, so their own row can be flagged
     */
    @Transactional(readOnly = true)
    public LeaderboardDTO build(Long adminId, LocalDate start, LocalDate end, String viewerEmail) {

        List<Employee> employees = employeeRepository.findByCompanyOf(adminId);

        // employeeId -> [resumes, sourced, contacted, connected, reports]
        Map<Long, long[]> totals = new LinkedHashMap<>();

        for (DailyReport report : dailyReportRepository.findByEmployeeInCompanyOf(adminId)) {
            LocalDate date = report.getReportDate();
            if (date == null || date.isBefore(start) || date.isAfter(end)) continue;

            long[] bucket = totals.computeIfAbsent(report.getEmployee().getEmployeeId(), key -> new long[5]);
            bucket[0] += nz(report.getProfilesSubmitted());
            bucket[1] += nz(report.getCandidatesSourced());
            bucket[2] += nz(report.getCandidatesContacted());
            bucket[3] += nz(report.getConnectedCount());
            bucket[4] += 1;
        }

        // Group by role. TreeMap keeps the role sections in a stable alphabetical order.
        Map<String, List<LeaderboardRowDTO>> byRole = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Employee employee : employees) {
            long[] bucket = totals.getOrDefault(employee.getEmployeeId(), new long[5]);
            String role = employee.getRole() == null || employee.getRole().isBlank()
                    ? "Unassigned"
                    : employee.getRole().trim();

            boolean isYou = viewerEmail != null && viewerEmail.equalsIgnoreCase(employee.getEmail());

            byRole.computeIfAbsent(role, key -> new ArrayList<>()).add(new LeaderboardRowDTO(
                    0, // rank assigned below, once the role group is complete
                    employee.getEmployeeId(),
                    employee.getEmployeeName(),
                    role,
                    bucket[0],
                    bucket[1],
                    bucket[2],
                    bucket[3],
                    bucket[4],
                    isYou
            ));
        }

        List<RoleLeaderboardDTO> roles = new ArrayList<>();

        for (Map.Entry<String, List<LeaderboardRowDTO>> entry : byRole.entrySet()) {
            List<LeaderboardRowDTO> rows = entry.getValue();

            // Highest resumes first; ties broken by reporting consistency, then name
            // so the order is stable rather than dependent on database row order.
            rows.sort(Comparator
                    .comparingLong(LeaderboardRowDTO::resumesSubmitted).reversed()
                    .thenComparing(Comparator.comparingLong(LeaderboardRowDTO::reportsFiled).reversed())
                    .thenComparing(LeaderboardRowDTO::employeeName, String.CASE_INSENSITIVE_ORDER));

            List<LeaderboardRowDTO> ranked = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                LeaderboardRowDTO r = rows.get(i);
                ranked.add(new LeaderboardRowDTO(
                        i + 1,
                        r.employeeId(),
                        r.employeeName(),
                        r.role(),
                        r.resumesSubmitted(),
                        r.candidatesSourced(),
                        r.candidatesContacted(),
                        r.connectedCount(),
                        r.reportsFiled(),
                        r.isYou()
                ));
            }

            roles.add(new RoleLeaderboardDTO(entry.getKey(), ranked.size(), ranked));
        }

        return new LeaderboardDTO(start, end, METRIC, roles);
    }

    private static long nz(Integer value) {
        return value == null ? 0 : value;
    }
}
