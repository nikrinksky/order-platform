package com.orderplatform.auth.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationExceptions_shouldReturnBadRequest() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "user");
        bindingResult.addError(new FieldError("user", "email", "Email is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Email is required", response.getBody().get("email"));
    }

    @Test
    void handleRuntimeException_shouldReturnConflict() {
        RuntimeException ex = new RuntimeException("User with email test@example.com already exists");

        ResponseEntity<Map<String, String>> response = handler.handleRuntimeException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("already exists"));
    }

    @Test
    void handleAccessDenied_shouldReturnForbidden() {
        AccessDeniedException ex = new AccessDeniedException("Access Denied");

        ResponseEntity<Map<String, String>> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Forbidden", response.getBody().get("error"));
    }

    @Test
    void handleUserNotFound_shouldReturnNotFound() {
        UserNotFoundException ex = new UserNotFoundException("User not found with id: 999");

        ResponseEntity<Map<String, String>> response = handler.handleUserNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("999"));
    }

    @Test
    void handleUserAlreadyExists_shouldReturnBadRequest() {
        UserAlreadyExistsException ex = new UserAlreadyExistsException("already exists");

        ResponseEntity<Map<String, String>> response = handler.handleUserAlreadyExists(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("already exists"));
    }

    /**
     * The dedicated types must be picked before the generic handler, otherwise a missing user
     * would be answered with 409 and a duplicate email with 409 as well.
     */
    @Test
    void dedicatedHandlers_areMoreSpecificThanTheGenericOne() {
        assertTrue(RuntimeException.class.isAssignableFrom(UserNotFoundException.class));
        assertTrue(RuntimeException.class.isAssignableFrom(UserAlreadyExistsException.class));
        assertEquals(HttpStatus.NOT_FOUND,
                handler.handleUserNotFound(new UserNotFoundException("x")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                handler.handleUserAlreadyExists(new UserAlreadyExistsException("x")).getStatusCode());
    }
}