package com.orderplatform.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the inventory-service API.
 *
 * <p>The Bearer scheme is declared once here and applied to every operation: the endpoints are
 * protected, and a document without it would suggest they can be called anonymously. Swagger UI
 * then offers the "Authorize" button, so a token can be pasted once and reused for every call.
 */
@SuppressWarnings("DesignForExtension")
@Configuration
public class OpenApiConfig {

    /** Name the operations refer to; must match the scheme added to the components. */
    private static final String SECURITY_SCHEME = "Bearer Authentication";

    /**
     * Builds the document served at {@code /v3/api-docs}.
     *
     * @return the OpenAPI description of this service
     */
    @Bean
    public OpenAPI inventoryServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Inventory Service API")
                        .version("1.0.0")
                        .description("Stock levels and per-order reservations"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, bearerScheme()));
    }

    private static SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .name(SECURITY_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Access token issued by auth-service");
    }
}
