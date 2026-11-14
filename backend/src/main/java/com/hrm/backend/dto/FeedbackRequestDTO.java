
package com.hrm.backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class FeedbackRequestDTO {

    private String client;

    private String candidateName;

    private String feedbackReceived;

    private String comments;
}
