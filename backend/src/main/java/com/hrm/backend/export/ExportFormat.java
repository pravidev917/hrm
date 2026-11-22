package com.hrm.backend.export;

import java.util.Locale;

/**
 * The two spreadsheet formats the admin tables can be downloaded in.
 *
 * CSV is a plain UTF-8 text file that any tool reads. XLSX is a real Excel
 * workbook, written without any third-party library (see {@link XlsxWriter}), so
 * the build stays dependency-free and works offline.
 */
public enum ExportFormat {

    CSV("csv", "text/csv;charset=UTF-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /** `?format=` as sent by the browser; anything unrecognised is a client error, not a silent CSV. */
    public static ExportFormat from(String value) {

        if (value == null || value.isBlank()) {
            return CSV;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "csv" -> CSV;
            case "xlsx", "xls", "excel" -> XLSX;
            default -> throw new IllegalArgumentException(
                    "Unsupported export format '" + value + "'. Use format=csv or format=xlsx.");
        };
    }
}
