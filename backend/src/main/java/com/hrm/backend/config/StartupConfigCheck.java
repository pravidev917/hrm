package com.hrm.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * Logs the effective security-relevant configuration once at startup and makes
 * unsafe production combinations impossible to miss in the journal.
 *
 * It only reads properties: it never changes behaviour and never fails startup,
 * so a misconfigured deploy still comes up and can be inspected rather than
 * looping on restart. Secrets are never printed.
 */
@Component
public class StartupConfigCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigCheck.class);

    private final Environment environment;

    @Value("${spring.jpa.hibernate.ddl-auto:unset}")
    private String ddlAuto;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cors.allowed-origins:}")
    private String corsOrigins;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    public StartupConfigCheck(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {

        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod"));

        log.info(
                "Runtime configuration: profiles={}, ddl-auto={}, cookie-secure={}, cors-origins={}, login-lock={} attempt(s)",
                Arrays.toString(environment.getActiveProfiles()),
                ddlAuto,
                cookieSecure,
                corsOrigins,
                environment.getProperty("app.login.max-attempts", "5"));

        if (!production) {
            return;
        }

        if (!"validate".equalsIgnoreCase(ddlAuto)) {
            log.error(
                    "PRODUCTION WARNING: ddl-auto is '{}'. Production must run with validate; "
                            + "the schema is applied from docs/migrations.",
                    ddlAuto);
        }

        if (!cookieSecure) {
            log.error("PRODUCTION WARNING: the auth cookie is not marked Secure. Set COOKIE_SECURE=true behind HTTPS.");
        }

        if (corsOrigins.toLowerCase(Locale.ROOT).contains("localhost")) {
            log.error("PRODUCTION WARNING: CORS still allows a localhost origin ({}). Set CORS_ALLOWED_ORIGINS.",
                    corsOrigins);
        }

        String normalisedSecret = jwtSecret == null ? "" : jwtSecret.toLowerCase(Locale.ROOT);

        if (normalisedSecret.isBlank()
                || normalisedSecret.contains("change-me")
                || normalisedSecret.contains("ci-only")) {
            log.error("PRODUCTION WARNING: JWT_SECRET looks like a placeholder. Rotate it before serving traffic.");
        }
    }
}