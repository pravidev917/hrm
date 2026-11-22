package com.hrm.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The platform names every form, table and chart shares.
 *
 * The rules pinned here are what keep old data readable: "Indeed" is now "Apna",
 * the free-text leftovers are "Others", and the canonical five are always in the
 * same display order.
 */
class SourcingPlatformsTests {

    @Test
    void canonicalListIsTheFivePromisedPlatformsInDisplayOrder() {
        assertEquals(
                List.of("Naukri", "Apna", "LinkedIn", "Careers", "Others"),
                SourcingPlatforms.CANONICAL);
    }

    @Test
    void canonicalNamesAreCaseInsensitiveAndTrimmed() {
        assertEquals("Apna", SourcingPlatforms.canonical(" apna ").orElseThrow());
        assertEquals("Naukri", SourcingPlatforms.canonical("NAUKRI").orElseThrow());
        assertEquals("Careers", SourcingPlatforms.canonical("Careers").orElseThrow());
    }

    @Test
    void indeedIsRetiredInFavourOfApna() {
        assertEquals("Apna", SourcingPlatforms.canonical("Indeed").orElseThrow());
        assertEquals("Apna", SourcingPlatforms.canonicalOrOthers("indeed"));
    }

    @Test
    void legacyFreeTextFallsIntoOthers() {
        assertEquals("Others", SourcingPlatforms.canonicalOrOthers("Referral"));
        assertEquals("Others", SourcingPlatforms.canonicalOrOthers("Company Website"));
        assertEquals("Others", SourcingPlatforms.canonicalOrOthers("Other"));
    }

    @Test
    void unknownPlatformsAreNotSilentlyAccepted() {
        assertTrue(SourcingPlatforms.canonical("Monster").isEmpty());
        assertFalse(SourcingPlatforms.canonical("").isPresent());
        assertFalse(SourcingPlatforms.canonical(null).isPresent());
        // Reading old rows still counts them, under "Others".
        assertEquals("Others", SourcingPlatforms.canonicalOrOthers("Monster"));
    }
}
