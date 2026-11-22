package com.hrm.backend.controller;

import com.hrm.backend.dto.SourcingAnalyticsDTO;
import com.hrm.backend.service.SourcingAnalyticsService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only sourcing analytics: profiles sourced per employee and per platform,
 * counted from daily reports over "first report → today".
 */
@RestController
@RequestMapping("/api/admin")
public class SourcingAnalyticsController {

    private final SourcingAnalyticsService sourcingAnalyticsService;

    public SourcingAnalyticsController(SourcingAnalyticsService sourcingAnalyticsService) {
        this.sourcingAnalyticsService = sourcingAnalyticsService;
    }

    @GetMapping("/sourcing")
    public ResponseEntity<SourcingAnalyticsDTO> sourcing(Authentication authentication) {
        return ResponseEntity.ok(
                sourcingAnalyticsService.getAnalytics(authentication.getName()));
    }
}
