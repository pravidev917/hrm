package com.hrm.backend.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight readiness endpoint for nginx health checks and deploy scripts.
 *
 * Returns 200 only when the database answers a trivial query, so a running JVM
 * with a broken database reports DOWN. It exposes no tenant data and is the
 * sole public endpoint besides the two sign-in routes.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {

        boolean dbUp = databaseReachable();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("time", Instant.now().toString());

        return dbUp
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(503).body(body);
    }

    private boolean databaseReachable() {

        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (DataAccessException exception) {
            return false;
        }
    }
}