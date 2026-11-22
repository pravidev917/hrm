package com.hrm.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the API answers when the request itself is wrong.
 *
 * These used to fall through to the catch-all handler and be reported as 500, so a
 * mistyped path or a GET on a POST-only endpoint looked like a backend crash. The
 * status of a business failure raised by a service is also pinned here, because the
 * frontend branches on 409 versus 400 to decide what to tell the user.
 */
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("an unknown path is 404, not 500")
    void unknownPathIsNotFound() {

        ResponseEntity<Map<String, Object>> response =
                handler.handleNoResource(
                        new NoResourceFoundException(HttpMethod.GET, "/api/nope", "No endpoint"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("a GET on a POST-only endpoint is 405 and names the allowed verb")
    void wrongMethodIsNotAllowed() {

        ResponseEntity<Map<String, Object>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).contains("POST"));
    }

    @Test
    @DisplayName("a body sent as XML is 415")
    void wrongContentTypeIsUnsupportedMediaType() {

        ResponseEntity<Map<String, Object>> response = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException(
                        MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON)));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
    }

    @Test
    @DisplayName("overlapping dates from a service are reported as 409 Conflict")
    void overlapIsConflict() {

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalState(
                new IllegalStateException(
                        "Leave dates overlap with an existing pending or approved request"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @DisplayName("business messages keep their own status: 403, 404, 400")
    void businessMessagesMapByIntent() {

        assertEquals(HttpStatus.FORBIDDEN, handler.handleIllegalState(
                new IllegalStateException("Access denied: Leave request does not belong to you"))
                .getStatusCode());

        assertEquals(HttpStatus.NOT_FOUND, handler.handleIllegalState(
                new IllegalStateException("Employee not found")).getStatusCode());

        assertEquals(HttpStatus.BAD_REQUEST, handler.handleIllegalState(
                new IllegalStateException("Only pending leave requests can be reviewed"))
                .getStatusCode());
    }

    @Test
    @DisplayName("a database failure never reaches the client with driver text")
    void databaseFailureIsGeneric() {

        ResponseEntity<Map<String, Object>> response = handler.handleDataAccess(
                new org.springframework.dao.DataIntegrityViolationException(
                        "ERROR: duplicate key value violates unique constraint \"employees_email_key\""));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getBody() == null ? null : response.getBody().get("fieldErrors"));
    }
}
