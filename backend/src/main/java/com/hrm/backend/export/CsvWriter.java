package com.hrm.backend.export;

import java.nio.charset.StandardCharsets;

/** Writes a sheet as UTF-8 CSV that opens cleanly in Excel, Numbers and Sheets. */
final class CsvWriter {

    private CsvWriter() {
    }

    static byte[] write(SheetBuilder sheet) {

        StringBuilder out = new StringBuilder("\uFEFF"); // BOM: Excel reads UTF-8 instead of guessing

        for (int i = 0; i < sheet.headers().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(sheet.headers().get(i)));
        }
        out.append('\n');

        for (var row : sheet.rows()) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(escape(SheetBuilder.text(row.get(i))));
            }
            out.append('\n');
        }

        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Quotes only when the value needs it, and stops a leading `=`, `+`, `-`, `@`
     * from being executed as a formula by the spreadsheet that opens the file.
     */
    static String escape(String value) {

        if (value == null) {
            return "";
        }

        String safe = value;
        if (!safe.isEmpty() && (safe.charAt(0) == '=' || safe.charAt(0) == '+'
                || safe.charAt(0) == '-' || safe.charAt(0) == '@')) {
            safe = "'" + safe;
        }

        return safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0
                ? '"' + safe.replace("\"", "\"\"") + '"'
                : safe;
    }
}
