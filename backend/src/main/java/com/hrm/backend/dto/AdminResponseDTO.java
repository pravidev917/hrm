package com.hrm.backend.dto;

import com.hrm.backend.entity.Admin;
import java.time.LocalDateTime;

/** Administrator representation for the administrators screen. Never contains the password hash. */
public record AdminResponseDTO(
        Long adminId,
        String adminName,
        String email,
        String contactDetails,
        String roleAccessLevel,
        String status,
        LocalDateTime createdAt
) {
    public static AdminResponseDTO from(Admin admin) {
        return new AdminResponseDTO(
                admin.getAdminId(),
                admin.getAdminName(),
                admin.getEmail(),
                admin.getContactDetails(),
                admin.getRoleAccessLevel(),
                admin.getStatus(),
                admin.getCreatedAt()
        );
    }
}