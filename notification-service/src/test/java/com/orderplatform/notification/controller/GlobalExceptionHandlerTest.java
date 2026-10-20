package com.orderplatform.notification.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntimeException_shouldReturnNotFound() {
        RuntimeException ex = new RuntimeException("Notification not found");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Notification not found", response.getBody().get("message"));
    }

    @Test
    void handleException_shouldReturnInternalServerError() {
        Exception ex = new Exception("Unexpected");

        ResponseEntity<Map<String, Object>> response = handler.handleException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Unexpected", response.getBody().get("message"));
    }

    /**
     * A rejected {@code @PreAuthorize} used to be answered with 404, because AccessDeniedException
     * is a RuntimeException and the generic handler owns that type. Reading someone else's
     * notifications must not look like "nothing there".
     */
    @Test
    void handleAccessDenied_shouldReturnForbidden() {
        AccessDeniedException ex = new AccessDeniedException("Access Denied");

        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Forbidden", response.getBody().get("error"));
    }
}