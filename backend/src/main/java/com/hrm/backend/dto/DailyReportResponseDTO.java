
package com.hrm.backend.dto;

import java.time.LocalDate;

public class DailyReportResponseDTO {

    private Long dailyReportId;
    private Long employeeId;
    private String employeeName;
    private Long clientId;
    private String clientName;
    private Integer profilesSubmitted;
    private Integer candidatesSourced;
    private Integer candidatesContacted;
    private Integer connectedCount;
    private LocalDate reportDate;
    private String remarks;

    public DailyReportResponseDTO() {
    }

    public DailyReportResponseDTO(
            Long dailyReportId,
            Long employeeId,
            String employeeName,
            Long clientId,
            String clientName,
            Integer profilesSubmitted,
            Integer candidatesSourced,
            Integer candidatesContacted,
            Integer connectedCount,
            LocalDate reportDate,
            String remarks) {

        this.dailyReportId = dailyReportId;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.clientId = clientId;
        this.clientName = clientName;
        this.profilesSubmitted = profilesSubmitted;
        this.candidatesSourced = candidatesSourced;
        this.candidatesContacted = candidatesContacted;
        this.connectedCount = connectedCount;
        this.reportDate = reportDate;
        this.remarks = remarks;
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
}
