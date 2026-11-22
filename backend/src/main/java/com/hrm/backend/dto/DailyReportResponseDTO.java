
package com.hrm.backend.dto;

import java.time.LocalDate;
import java.util.List;

public class DailyReportResponseDTO {

    private Long dailyReportId;
    private Long employeeId;
    private String employeeName;
    /** Employee code (EMP001) - the "Employee ID" column of the reports table. */
    private String employeeCode;
    /** Employee designation - the "Designation" column of the reports table. */
    private String employeeDesignation;
    private Long clientId;
    private String clientName;
    private Integer profilesSubmitted;
    private Integer candidatesSourced;
    private Integer candidatesContacted;
    private Integer connectedCount;
    private LocalDate reportDate;
    private String remarks;
    private String sourcingPlatform;
    private List<PlatformCountDTO> platformCounts;

    public DailyReportResponseDTO() {
    }

    public DailyReportResponseDTO(
            Long dailyReportId,
            Long employeeId,
            String employeeName,
            String employeeCode,
            String employeeDesignation,
            Long clientId,
            String clientName,
            Integer profilesSubmitted,
            Integer candidatesSourced,
            Integer candidatesContacted,
            Integer connectedCount,
            LocalDate reportDate,
            String remarks,
            String sourcingPlatform,
            List<PlatformCountDTO> platformCounts) {

        this.dailyReportId = dailyReportId;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employeeCode = employeeCode;
        this.employeeDesignation = employeeDesignation;
        this.clientId = clientId;
        this.clientName = clientName;
        this.profilesSubmitted = profilesSubmitted;
        this.candidatesSourced = candidatesSourced;
        this.candidatesContacted = candidatesContacted;
        this.connectedCount = connectedCount;
        this.reportDate = reportDate;
        this.remarks = remarks;
        this.sourcingPlatform = sourcingPlatform;
        this.platformCounts = platformCounts;
    }

    public Long getDailyReportId() {
        return dailyReportId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getEmployeeDesignation() {
        return employeeDesignation;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public Integer getProfilesSubmitted() {
        return profilesSubmitted;
    }

    public Integer getCandidatesSourced() {
        return candidatesSourced;
    }

    public Integer getCandidatesContacted() {
        return candidatesContacted;
    }

    public Integer getConnectedCount() {
        return connectedCount;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public String getSourcingPlatform() {
        return sourcingPlatform;
    }

    public List<PlatformCountDTO> getPlatformCounts() {
        return platformCounts;
    }
}
