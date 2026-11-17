package com.hrm.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory throttle for sign-in attempts.
 *
 * Keyed by "identifier|client-address" so one attacker cannot lock an account
 * out for everyone, and so rotating identifiers from a single address is still
 * slowed down. After {@code app.login.max-attempts} failures the key is locked
 * for {@code app.login.lock-minutes}; a successful sign-in clears the counter.
 *
 * Scope note: state is per process. A multi-instance deployment needs a shared
 * store (Redis) or a gateway-level rate limit as well.
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration lockDuration;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.login.max-attempts:5}") int maxAttempts,
            @Value("${app.login.lock-minutes:15}") long lockMinutes) {

        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockDuration = Duration.ofMinutes(Math.max(1, lockMinutes));
    }

    /** Throws 429 when the key is currently locked. */
    public void assertNotLocked(String identifier, String clientAddress) {

        Attempt attempt = attempts.get(key(identifier, clientAddress));

        if (attempt == null) {
            return;
        }

        if (attempt.isLocked()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many sign-in attempts. Try again in "
                            + attempt.remainingMinutes() + " minute(s).");
        }

        if (attempt.isStale()) {
            attempts.remove(key(identifier, clientAddress));
        }
    }

    public void recordFailure(String identifier, String clientAddress) {

        String key = key(identifier, clientAddress);

        attempts.compute(key, (ignored, attempt) -> {
            // Keep an active counter, start over once a lock has run out.
            Attempt next = (attempt == null || attempt.isStale())
                    ? new Attempt()
                    : attempt;
            next.failures++;
            if (next.failures >= maxAttempts) {
                next.lockedUntil = Instant.now().plus(lockDuration);
            }
            return next;
        });

        pruneIfLarge();
    }

    public void recordSuccess(String identifier, String clientAddress) {
        attempts.remove(key(identifier, clientAddress));
    }

    private String key(String identifier, String clientAddress) {
        String id = identifier == null ? "" : identifier.trim().toLowerCase();
        String address = clientAddress == null ? "unknown" : clientAddress;
        return id + "|" + address;
    }

    /** Keeps the map from growing without bound under a spray attack. */
    private void pruneIfLarge() {
        if (attempts.size() < 10_000) {
            return;
        }
        attempts.entrySet().removeIf(entry -> entry.getValue().isStale());
    }

    private static final class Attempt {
        private int failures;
        private Instant lockedUntil;

        private boolean isLocked() {
            return lockedUntil != null && lockedUntil.isAfter(Instant.now());
        }

        /** A lock that has run out: the counter is starting again. */
        private boolean isStale() {
            return lockedUntil != null && lockedUntil.isBefore(Instant.now());
        }

        private long remainingMinutes() {
            if (lockedUntil == null) {
                return 1;
            }
            long seconds = Duration.between(Instant.now(), lockedUntil).toSeconds();
            return Math.max(1, (seconds + 59) / 60);
        }
    }
}
