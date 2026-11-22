package com.hrm.backend.export;

/**
 * The same "contains, ignoring case" search the tables do in the browser, so an
 * export never contains more or less than what the administrator is looking at.
 */
public final class TextFilter {

    private TextFilter() {
    }

    /** True when the query is blank, or any of the values contains it (case-insensitive). */
    public static boolean contains(String query, Object... values) {

        if (query == null || query.isBlank()) {
            return true;
        }

        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);

        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (SheetBuilder.text(value).toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public static boolean same(String expected, String actual) {

        if (expected == null || expected.isBlank()) {
            return true;
        }
        return actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }
}
