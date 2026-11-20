package com.hrm.backend.security;

import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.EmployeeRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization at the door.
 *
 * The API is stateless and exposes no session to revoke, so the JWT filter is what
 * makes a deactivation effective: it re-reads the account on every request and
 * only grants a role when the row still exists and is active. These cases pin
 * that behaviour for both roles.
 */
class JwtAuthenticationFilterTests {

    private static final String EMAIL = "grace@example.com";
    private static final String TOKEN = "token";

    private JwtService jwtService;
    private AdminRepository admins;
    private EmployeeRepository employees;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        admins = mock(AdminRepository.class);
        employees = mock(EmployeeRepository.class);
        filter = new JwtAuthenticationFilter(jwtService, admins, employees);

        when(jwtService.extractEmail(TOKEN)).thenReturn(EMAIL);
        when(jwtService.isTokenValid(TOKEN, EMAIL)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest requestWithCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("HRM_AUTH", TOKEN));
        return request;
    }

    private Authentication authenticate() throws Exception {
        MockHttpServletRequest request = requestWithCookie();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void grantsAdminAuthorityToAnActiveAdmin() throws Exception {

        when(jwtService.extractRole(TOKEN)).thenReturn("ADMIN");
        when(jwtService.extractUserId(TOKEN)).thenReturn(1L);
        when(admins.findById(1L)).thenReturn(Optional.of(admin(1L, "ACTIVE")));

        Authentication authentication = authenticate();

        assertEquals(EMAIL, authentication.getName());
        assertEquals(List.of("ROLE_ADMIN"),
                authentication.getAuthorities().stream().map(Object::toString).toList());
    }

    @Test
    void refusesAnInactiveAdminSoDeactivationEndsTheirSession() throws Exception {

        when(jwtService.extractRole(TOKEN)).thenReturn("ADMIN");
        when(jwtService.extractUserId(TOKEN)).thenReturn(2L);
        when(admins.findById(2L)).thenReturn(Optional.of(admin(2L, "INACTIVE")));

        assertNull(authenticate());
    }

    @Test
    void refusesATokenWhoseAdminRowIsGone() throws Exception {

        when(jwtService.extractRole(TOKEN)).thenReturn("ADMIN");
        when(jwtService.extractUserId(TOKEN)).thenReturn(3L);
        when(admins.findById(3L)).thenReturn(Optional.empty());

        assertNull(authenticate());
    }

    @Test
    void refusesAnAdminTokenWhenTheEmailNoLongerMatches() throws Exception {

        Admin renamed = admin(4L, "ACTIVE");
        renamed.setEmail("someone-else@example.com");
        when(jwtService.extractRole(TOKEN)).thenReturn("ADMIN");
        when(jwtService.extractUserId(TOKEN)).thenReturn(4L);
        when(admins.findById(4L)).thenReturn(Optional.of(renamed));

        assertNull(authenticate());
    }

    @Test
    void grantsEmployeeAuthorityToAnApprovedEmployee() throws Exception {

        Employee employee = new Employee();
        employee.setEmployeeId(9L);
        employee.setEmail(EMAIL);
        employee.setAccountStatus("APPROVED");

        when(jwtService.extractRole(TOKEN)).thenReturn("EMPLOYEE");
        when(jwtService.extractUserId(TOKEN)).thenReturn(9L);
        when(employees.findById(9L)).thenReturn(Optional.of(employee));

        Authentication authentication = authenticate();

        assertEquals(List.of("ROLE_EMPLOYEE"),
                authentication.getAuthorities().stream().map(Object::toString).toList());
    }

    @Test
    void refusesWhenThereIsNoAuthCookie() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
        verify(jwtService, org.mockito.Mockito.never()).extractEmail(any());
    }

    @Test
    void refusesAnInvalidToken() throws Exception {

        when(jwtService.isTokenValid(eq(TOKEN), eq(EMAIL))).thenReturn(false);
        when(jwtService.extractRole(TOKEN)).thenReturn("ADMIN");
        when(jwtService.extractUserId(TOKEN)).thenReturn(1L);

        assertNull(authenticate());
    }

    private static Admin admin(Long id, String status) {
        Admin admin = new Admin();
        admin.setAdminId(id);
        admin.setEmail(EMAIL);
        admin.setStatus(status);
        return admin;
    }
}
