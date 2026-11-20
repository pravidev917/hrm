package com.hrm.backend.dto;

import com.hrm.backend.entity.Admin;
import java.time.LocalDateTime;

/**
 * Administrator representation for the administrators screen.
 *
 * Never contains the password hash. The company is read-only context: it tells the
 * console which shared workspace the account belongs to.
 */
public record AdminResponseDTO(
        Long adminId,
        String adminName,
        String email,
        String contactDetails,
        String roleAccessLevel,
        String status,
        Long companyId,
        String companyName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminResponseDTO from(Admin admin) {
        return new AdminResponseDTO(
                admin.getAdminId(),
                admin.getAdminName(),
                admin.getEmail(),
                admin.getContactDetails(),
                admin.getRoleAccessLevel(),
                admin.getStatus(),
                admin.getCompany() == null ? null : admin.getCompany().getCompanyId(),
                admin.getCompany() == null ? null : admin.getCompany().getCompanyName(),
                admin.getCreatedAt(),
                admin.getUpdatedAt()
        );
    }
}
