
package com.hrm.backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class FollowupRequestDTO {

    private String candidateName;

    private String client;

    private LocalDate joinDate;

    private String joiningStatus;

    private String reason;
}
