/**
 * OpenAPI configuration class for Auth Service API documentation.
 */
package com.orderplatform.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration class for Auth Service API documentation.
 */
@SuppressWarnings("DesignForExtension")
@Configuration
public class OpenAPIConfig {

    /**
     * Customizes the OpenAPI specification for Auth Service.
     *
     * @return configured OpenAPI bean
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Auth Service API")
                        .version("1.0.0")
                        .description("Authentication and Authorization Service API")
                        .contact(new Contact()
                                .name("Order Platform Team")
                                .email("support@orderplatform.com")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .name("Bearer Authentication")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token like: bearer {token}")));
    }
}
