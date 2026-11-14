
package com.hrm.backend.security;

import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.EmployeeRepository;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminRepository adminRepository;
    private final EmployeeRepository employeeRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AdminRepository adminRepository,
            EmployeeRepository employeeRepository) {

        this.jwtService = jwtService;
        this.adminRepository = adminRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String token = getTokenFromCookie(request);

        if (token != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                String email = jwtService.extractEmail(token);
                String role = jwtService.extractRole(token);
                Long userId = jwtService.extractUserId(token);

                if (email != null &&
                        role != null &&
                        userId != null &&
                        jwtService.isTokenValid(token, email) &&
                        isAccountActive(email, role, userId)) {

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority(
                                                    "ROLE_" + role
                                            )
                                    )
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }

            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    // CHECK CURRENT ACCOUNT STATUS IN DATABASE
    private boolean isAccountActive(
            String email,
            String role,
            Long userId) {

        if ("ADMIN".equals(role)) {

            return adminRepository.findById(userId)
                    .filter(admin -> admin.getEmail().equals(email))
                    .filter(admin -> "ACTIVE".equals(admin.getStatus()))
                    .isPresent();
        }

        if ("EMPLOYEE".equals(role)) {

            return employeeRepository.findById(userId)
                    .filter(employee -> employee.getEmail().equals(email))
                    .filter(employee ->
                            "APPROVED".equals(employee.getAccountStatus()))
                    .isPresent();
        }

        return false;
    }

    // EXTRACT JWT FROM HTTP-ONLY COOKIE
    private String getTokenFromCookie(HttpServletRequest request) {

        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {

            if ("HRM_AUTH".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
