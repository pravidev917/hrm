package com.hrm.backend.config;

import com.hrm.backend.entity.Admin;
import com.hrm.backend.repository.AdminRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Seeds the first administrator account.
 *
 * The application has no public sign-up and only administrators can create
 * accounts, so an empty admins table would make the system unusable. On start
 * up, when no administrator exists, this creates one from configuration:
 *
 *   DEFAULT_ADMIN_EMAIL     (required to seed)
 *   DEFAULT_ADMIN_PASSWORD  (required to seed, at least 12 characters, not a
 *                            well-known default and never equal to the email)
 *   DEFAULT_ADMIN_NAME      (optional, defaults to "Administrator")
 *
 * It never touches an existing administrator, so it is safe to run on every boot.
 */
@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${DEFAULT_ADMIN_EMAIL:}")
    private String defaultEmail;

    @Value("${DEFAULT_ADMIN_PASSWORD:}")
    private String defaultPassword;

    @Value("${DEFAULT_ADMIN_NAME:Administrator}")
    private String defaultName;

    public DefaultAdminInitializer(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (adminRepository.count() > 0) {
            return;
        }

        if (defaultEmail.isBlank() || defaultPassword.isBlank()) {
            log.warn(
                    "No administrator account exists and DEFAULT_ADMIN_EMAIL / DEFAULT_ADMIN_PASSWORD are not set. "
                            + "Set both environment variables (or insert an admin row) to sign in.");
            return;
        }

        if (!isStrongEnough(defaultEmail, defaultPassword)) {
            log.warn(
                    "DEFAULT_ADMIN_PASSWORD is too weak: use at least 12 characters, "
                            + "avoid well-known defaults and do not reuse the email address. "
                            + "Skipping administrator seeding.");
            return;
        }

        Admin admin = new Admin();
        admin.setAdminName(defaultName);
        admin.setEmail(defaultEmail.trim().toLowerCase(Locale.ROOT));
        admin.setPassword(passwordEncoder.encode(defaultPassword));
        admin.setRoleAccessLevel("ADMIN");
        admin.setStatus("ACTIVE");

        adminRepository.save(admin);

        log.info("Seeded the initial administrator account {}", admin.getEmail());
        log.info("Sign in once and change this password if it came from a document or chat.");
    }

    /** Minimum bar for the very first administrator credential. */
    private boolean isStrongEnough(String email, String password) {

        if (password.length() < 12) {
            return false;
        }

        String normalised = password.toLowerCase(Locale.ROOT);
        String emailLocal = email.split("@")[0].toLowerCase(Locale.ROOT);

        return !normalised.equals(emailLocal)
                && !normalised.contains("password")
                && !normalised.contains("admin123")
                && !normalised.contains("changeme")
                && !normalised.contains("letmein");
    }
}
