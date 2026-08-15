package com.orderplatform.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAPIConfigTest {

    private final OpenAPIConfig config = new OpenAPIConfig();

    @Test
    void shouldCreateOpenApi() {
        OpenAPI api = config.customOpenAPI();

        assertNotNull(api);
        assertNotNull(api.getInfo());
        assertEquals("Auth Service API", api.getInfo().getTitle());
        assertEquals("1.0.0", api.getInfo().getVersion());
        assertNotNull(api.getComponents());
        Components components = api.getComponents();
        assertTrue(components.getSecuritySchemes().containsKey("Bearer Authentication"));
    }
}