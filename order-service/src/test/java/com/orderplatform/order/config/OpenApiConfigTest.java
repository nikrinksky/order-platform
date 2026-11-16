package com.orderplatform.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void shouldDescribeTheService() {
        OpenAPI api = config.orderServiceOpenApi();

        assertNotNull(api.getInfo());
        assertEquals("Order Service API", api.getInfo().getTitle());
        assertEquals("1.0.0", api.getInfo().getVersion());
    }

    /** Without the scheme the UI has no "Authorize" button and every protected call looks public. */
    @Test
    void shouldDeclareTheBearerScheme() {
        OpenAPI api = config.orderServiceOpenApi();

        assertTrue(api.getComponents().getSecuritySchemes().containsKey("Bearer Authentication"));
        assertTrue(api.getSecurity().stream()
                .anyMatch(requirement -> requirement.containsKey("Bearer Authentication")));
    }
}
