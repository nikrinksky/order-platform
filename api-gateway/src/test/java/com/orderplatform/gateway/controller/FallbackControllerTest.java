package com.orderplatform.gateway.controller;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void authFallback_shouldReturn503() {
        Map<String, String> response = controller.authFallback().block();

        assertNotNull(response);
        assertEquals("503", response.get("status"));
        assertTrue(response.get("error").contains("Auth service"));
    }

    @Test
    void userFallback_shouldReturn503() {
        Map<String, String> response = controller.userFallback().block();

        assertNotNull(response);
        assertEquals("503", response.get("status"));
        assertTrue(response.get("error").contains("User service"));
    }

    @Test
    void productFallback_shouldReturn503() {
        Map<String, String> response = controller.productFallback().block();

        assertNotNull(response);
        assertEquals("503", response.get("status"));
        assertTrue(response.get("error").contains("Product service"));
    }
}