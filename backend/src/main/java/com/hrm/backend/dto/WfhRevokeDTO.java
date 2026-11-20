package com.hrm.backend.dto;

import jakarta.validation.constraints.Size;

/** Optional reason recorded when an administrator revokes an approved WFH request. */
public record WfhRevokeDTO(

        @Size(max = 2000, message = "Comment cannot exceed 2000 characters")
        String comment
) {
}
