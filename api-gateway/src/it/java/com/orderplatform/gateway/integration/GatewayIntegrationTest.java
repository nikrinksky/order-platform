package com.orderplatform.gateway.integration;

import com.orderplatform.gateway.AbstractGatewayIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;

/**
 * Integration tests for API Gateway.
 */
class GatewayIntegrationTest extends AbstractGatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void fallbackAuth_shouldReturn503() {
        webTestClient.get()
                .uri(URI.create("http://localhost:" + port + "/fallback/auth"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.error").isEqualTo("Auth service is unavailable. Please try again later.");
    }

    @Test
    void fallbackUser_shouldReturn503() {
        webTestClient.get()
                .uri(URI.create("http://localhost:" + port + "/fallback/user"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.error").isEqualTo("User service is unavailable. Please try again later.");
    }

    @Test
    void fallbackProduct_shouldReturn503() {
        webTestClient.get()
                .uri(URI.create("http://localhost:" + port + "/fallback/product"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.error").isEqualTo("Product service is unavailable. Please try again later.");
    }
}