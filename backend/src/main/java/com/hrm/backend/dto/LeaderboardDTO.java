package com.hrm.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Leaderboard of an admin's team, ranked inside each job designation.
 *
 * Employees with different roles are not compared with each other: a sourcer and a
 * senior recruiter do not share targets, so ranking happens per role.
 *
 * @param periodStart first day of the period
 * @param periodEnd   last day of the period
 * @param metric      which number the rank is based on
 * @param roles       one entry per job designation, each already ranked
 */
public record LeaderboardDTO(
        LocalDate periodStart,
        LocalDate periodEnd,
        String metric,
        List<RoleLeaderboardDTO> roles
) {
    /**
     * @param rank      1-based position within the role
     * @param isYou     true for the requesting employee, so the UI can highlight them
     */
    public record LeaderboardRowDTO(
            int rank,
            Long employeeId,
            String employeeName,
            String role,
            long resumesSubmitted,
            long candidatesSourced,
            long candidatesContacted,
            long connectedCount,
            long reportsFiled,
            boolean isYou
    ) {
    }

    public record RoleLeaderboardDTO(
            String role,
            int employeeCount,
            List<LeaderboardRowDTO> rows
    ) {
    }
}
