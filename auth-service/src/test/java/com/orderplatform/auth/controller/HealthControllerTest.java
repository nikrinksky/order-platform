package com.orderplatform.auth.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void health_shouldReturnUp() {
        Map<String, String> result = controller.health();

        assertEquals("UP", result.get("status"));
        assertEquals("auth-service", result.get("service"));
    }

    @Test
    void info_shouldReturnServiceInfo() {
        Map<String, String> result = controller.info();

        assertEquals("Auth Service", result.get("name"));
        assertEquals("1.0.0", result.get("version"));
        assertEquals("Authentication and Authorization Service", result.get("description"));
    }
}