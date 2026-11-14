
package com.hrm.backend.dto;

import com.hrm.backend.entity.Followup;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class FollowupResponseDTO {

    private Long followupId;
    private Long employeeId;
    private String employeeName;
    private String candidateName;
    private String client;
    private LocalDate joinDate;
    private String joiningStatus;
    private String reason;

    public static FollowupResponseDTO from(Followup followup) {

        FollowupResponseDTO dto = new FollowupResponseDTO();

        dto.setFollowupId(followup.getFollowupId());
        dto.setEmployeeId(followup.getEmployee().getEmployeeId());
        dto.setEmployeeName(followup.getEmployee().getEmployeeName());
        dto.setCandidateName(followup.getCandidateName());
        dto.setClient(followup.getClient());
        dto.setJoinDate(followup.getJoinDate());
        dto.setJoiningStatus(followup.getJoiningStatus());
        dto.setReason(followup.getReason());

        return dto;
    }
}
