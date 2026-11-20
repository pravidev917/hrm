package com.hrm.backend.controller;

import com.hrm.backend.dto.AdminResponseDTO;
import com.hrm.backend.dto.AdminUpdateRequest;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.EmployeeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who the endpoints act as, and what they hand back.
 *
 * The acting administrator is always resolved from the authenticated session, so a
 * request body cannot name somebody else as the caller. The response DTO is also
 * checked here so a password hash can never slip into an API response.
 */
class AdminControllerActorTests {

    private AdminService adminService;
    private AdminController controller;
    private Authentication session;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminService.class);
        controller = new AdminController(mock(EmployeeService.class), adminService);

        session = new UsernamePasswordAuthenticationToken(
                "grace@example.com", null, List.of());

        when(adminService.getAdminIdByEmail("grace@example.com")).thenReturn(7L);
    }

    private static Admin admin(long id) {
        Admin admin = new Admin();
        admin.setAdminId(id);
        admin.setAdminName("Grace");
        admin.setEmail("grace@example.com");
        admin.setPassword("$2a$10$not-a-real-hash");
        admin.setStatus("ACTIVE");
        return admin;
    }

    @Test
    void updateActsAsTheSignedInAdministrator() {

        AdminUpdateRequest request = new AdminUpdateRequest();
        request.setAdminName("Grace");
        request.setEmail("grace@example.com");

        when(adminService.updateAdminAccount(any(), any(), any())).thenReturn(admin(2L));

        controller.updateAdmin(2L, request, session);

        verify(adminService).updateAdminAccount(7L, 2L, request);
    }

    @Test
    void deactivateActsAsTheSignedInAdministrator() {

        when(adminService.changeAdminStatus(any(), any(), any(Boolean.class))).thenReturn(admin(2L));

        controller.deactivateAdmin(2L, session);

        verify(adminService).changeAdminStatus(7L, 2L, false);
    }

    @Test
    void activateActsAsTheSignedInAdministrator() {

        when(adminService.changeAdminStatus(any(), any(), any(Boolean.class))).thenReturn(admin(2L));

        controller.activateAdmin(2L, session);

        verify(adminService).changeAdminStatus(7L, 2L, true);
    }

    @Test
    void deleteActsAsTheSignedInAdministrator() {

        Map<String, String> body = controller.deleteAdmin(2L, session).getBody();

        verify(adminService).deleteAdminAccount(7L, 2L);
        assertEquals("Administrator deleted", body.get("message"));
    }

    @Test
    void responseNeverCarriesThePasswordHash() {

        AdminResponseDTO response = AdminResponseDTO.from(admin(2L));

        List<String> fields = Arrays.stream(AdminResponseDTO.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(Locale.ROOT))
                .toList();

        assertFalse(fields.contains("password"), "AdminResponseDTO must not expose a password field");
        assertFalse(fields.contains("passwordhash"));
        assertTrue(fields.contains("status"));
    }
}
