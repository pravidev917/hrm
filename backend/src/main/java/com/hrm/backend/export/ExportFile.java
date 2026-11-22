package com.hrm.backend.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * A finished download: the bytes, the file name and the media type.
 *
 * Controllers only build the {@link SheetBuilder} for their table and return
 * {@code ExportFile.of(sheet, format, "daily-reports").toResponse()}.
 */
public record ExportFile(byte[] body, String filename, ExportFormat format) {

    public static ExportFile of(SheetBuilder sheet, ExportFormat format, String baseName) {

        byte[] body = format == ExportFormat.XLSX ? XlsxWriter.write(sheet) : CsvWriter.write(sheet);
        String filename = baseName + "-" + LocalDate.now() + "." + format.extension();
        return new ExportFile(body, filename, format);
    }

    /** `?format=` straight from the query string, so every export endpoint parses it the same way. */
    public static ExportFile of(SheetBuilder sheet, String formatParam, String baseName) {
        return of(sheet, ExportFormat.from(formatParam), baseName);
    }

    public ResponseEntity<byte[]> toResponse() {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(format.contentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename);
        headers.setContentLength(body.length);
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /** For logs and tests: how many bytes were written. */
    public int size() {
        return body.length;
    }

    public String describe() {
        return filename + " (" + body.length + " bytes, " + StandardCharsets.UTF_8.name() + ")";
    }
}
