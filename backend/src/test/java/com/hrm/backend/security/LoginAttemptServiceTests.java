package com.hrm.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The sign-in throttle is the only control that slows down password guessing,
 * so its boundaries are pinned here: lock after N failures, stay scoped to the
 * identifier/address pair, and clear on success.
 */
class LoginAttemptServiceTests {

    private static final String ADDRESS = "203.0.113.10";

    @Test
    void locksAfterTheConfiguredNumberOfFailures() {

        LoginAttemptService service = new LoginAttemptService(3, 15);

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertDoesNotThrow(() -> service.assertNotLocked("user@example.com", ADDRESS));
            service.recordFailure("user@example.com", ADDRESS);
        }

        ResponseStatusException locked = assertThrows(ResponseStatusException.class,
                () -> service.assertNotLocked("user@example.com", ADDRESS));

        assertEquals(429, locked.getStatusCode().value());
    }

    @Test
    void lockIsScopedToTheIdentifierAndAddress() {

        LoginAttemptService service = new LoginAttemptService(2, 15);

        service.recordFailure("victim@example.com", ADDRESS);
        service.recordFailure("victim@example.com", ADDRESS);

        // Same account from another address is still allowed: one attacker
        // cannot lock a real user out of their own account.
        assertDoesNotThrow(() -> service.assertNotLocked("victim@example.com", "198.51.100.7"));

        // A different identifier from the locked address is still allowed.
        assertDoesNotThrow(() -> service.assertNotLocked("someone-else@example.com", ADDRESS));

        assertThrows(ResponseStatusException.class,
                () -> service.assertNotLocked("victim@example.com", ADDRESS));
    }

    @Test
    void successfulSignInClearsTheCounter() {

        LoginAttemptService service = new LoginAttemptService(3, 15);

        service.recordFailure("user@example.com", ADDRESS);
        service.recordFailure("user@example.com", ADDRESS);
        service.recordSuccess("user@example.com", ADDRESS);

        // Two more failures must not add up to the previous two.
        service.recordFailure("user@example.com", ADDRESS);
        service.recordFailure("user@example.com", ADDRESS);

        assertDoesNotThrow(() -> service.assertNotLocked("user@example.com", ADDRESS));
    }

    @Test
    void identifiersAreCaseAndWhitespaceInsensitive() {

        LoginAttemptService service = new LoginAttemptService(1, 15);

        service.recordFailure("  User@Example.com ", ADDRESS);

        assertThrows(ResponseStatusException.class,
                () -> service.assertNotLocked("user@example.COM", ADDRESS));
    }
}
