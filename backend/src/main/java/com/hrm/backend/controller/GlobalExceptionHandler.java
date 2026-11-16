package com.hrm.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single place that turns exceptions into the JSON error shape the frontend reads:
 * { "message": "...", "fieldErrors": { "field": "message" } } (fieldErrors only for validation).
 *
 * Status codes follow the intent expressed by the service layer:
 *  - ResponseStatusException keeps its own status (401/403/404/409...)
 *  - Access violations map to 403, missing rows to 404, bad input to 400
 * Anything unexpected becomes 500 instead of being reported as a client error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException exception) {

        String message = exception.getReason() != null
                ? exception.getReason()
                : exception.getStatusCode().toString();

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of("message", message));
    }

    /** Bean-validation failures on @Valid request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException exception) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        String first = fieldErrors.values().stream().findFirst().orElse("Invalid request");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", first);
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Malformed JSON, wrong enum/date formats in query parameters. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, Object>> handleUnreadable(Exception exception) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Request could not be read. Check the values you sent.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Explicit validation and business-rule failures raised by services. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", messageOf(exception, "Invalid request")));
    }

    /**
     * Existing services signal "not yours" / "not found" / "not allowed" with
     * IllegalStateException and plain RuntimeException. Map them by intent so the
     * client can distinguish 403/404 from a genuine 400.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException exception) {

        String message = messageOf(exception, "Request cannot be completed");

        return ResponseEntity.status(statusFor(message)).body(Map.of("message", message));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException exception) {

        String message = messageOf(exception, "Request could not be processed");

        return ResponseEntity.status(statusFor(message)).body(Map.of("message", message));
    }

    private String messageOf(RuntimeException exception, String fallback) {
        return exception.getMessage() != null && !exception.getMessage().isBlank()
                ? exception.getMessage()
                : fallback;
    }

    private HttpStatus statusFor(String message) {
        String lower = message.toLowerCase();

        if (lower.contains("access denied") || lower.contains("does not belong")) {
            return HttpStatus.FORBIDDEN;
        }
        if (lower.contains("not found")) {
            return HttpStatus.NOT_FOUND;
        }
        if (lower.contains("already exists") || lower.contains("duplicate")) {
            return HttpStatus.CONFLICT;
        }
        if (lower.contains("not approved") || lower.contains("not active")) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
