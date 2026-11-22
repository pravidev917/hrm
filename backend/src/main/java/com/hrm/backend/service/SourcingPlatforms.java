package com.hrm.backend.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The sourcing platforms the business uses, in one place.
 *
 * Everything that names a platform - daily report forms, admin dashboards, charts,
 * validation and the candidate tracker - goes through this class, so a report filed
 * as "Indeed" and a chart looking for "Apna" can never disagree again.
 *
 * The five canonical names are fixed on purpose: they are the columns of the
 * sourcing analytics table. Anything else that arrives in old data is folded into
 * "Others" instead of creating a sixth column that nobody looks at.
 */
public final class SourcingPlatforms {

    public static final String NAUKRI = "Naukri";
    public static final String APNA = "Apna";
    public static final String LINKEDIN = "LinkedIn";
    public static final String CAREERS = "Careers";
    public static final String OTHERS = "Others";

    /** Display order used by every form, table and chart. */
    public static final List<String> CANONICAL = List.of(NAUKRI, APNA, LINKEDIN, CAREERS, OTHERS);

    /**
     * Retired names and the platform they now belong to. "Indeed" was replaced by
     * "Apna"; the free-text leftovers collapse into "Others".
     */
    private static final Map<String, String> RETIRED = Map.of(
            "indeed", APNA,
            "referral", OTHERS,
            "referrals", OTHERS,
            "company website", OTHERS,
            "company site", OTHERS,
            "other", OTHERS,
            "others", OTHERS,
            "website", OTHERS);

    private SourcingPlatforms() {
    }

    /**
     * Canonical name for a platform written by a client or stored earlier.
     *
     * @return the canonical spelling, or empty when the name is not one we accept
     */
    public static Optional<String> canonical(String raw) {

        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String value = raw.trim().toLowerCase(Locale.ROOT);

        for (String name : CANONICAL) {
            if (name.toLowerCase(Locale.ROOT).equals(value)) {
                return Optional.of(name);
            }
        }

        return Optional.ofNullable(RETIRED.get(value));
    }

    /**
     * Canonical name, folding retired names in and treating an unknown value as
     * "Others". Used when reading old rows so analytics never lose a count.
     */
    public static String canonicalOrOthers(String raw) {
        return canonical(raw).orElse(OTHERS);
    }

    /** Human-readable list for validation messages. */
    public static String allowedList() {
        return String.join(", ", CANONICAL);
    }
}
