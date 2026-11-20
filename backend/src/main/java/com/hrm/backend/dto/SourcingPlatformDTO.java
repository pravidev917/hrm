package com.hrm.backend.dto;

import com.hrm.backend.entity.SourcingPlatform;

public record SourcingPlatformDTO(
        Long sourcingPlatformId,
        String name,
        Boolean active,
        Integer sortOrder
) {
    public static SourcingPlatformDTO from(SourcingPlatform platform) {
        return new SourcingPlatformDTO(
                platform.getSourcingPlatformId(),
                platform.getName(),
                platform.getActive(),
                platform.getSortOrder());
    }
}
