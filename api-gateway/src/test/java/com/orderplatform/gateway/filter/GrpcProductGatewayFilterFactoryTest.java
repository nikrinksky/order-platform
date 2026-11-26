package com.orderplatform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.gateway.client.ProductCatalogClient;
import com.orderplatform.gateway.client.ProductCatalogClient.ProductView;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the route filter that answers a product page from gRPC.
 *
 * <p>The filter sits on a route that also carries the list, the search and every write. The most
 * important assertions here are therefore the negative ones: anything that is not exactly
 * {@code GET /api/products/{id}} has to reach the proxy untouched.
 */
class GrpcProductGatewayFilterFactoryTest {

    private ProductCatalogClient client;
    private GatewayFilter filter;

    @BeforeEach
    void setUp() {
        client = mock(ProductCatalogClient.class);
        filter = new GrpcProductGatewayFilterFactory(client, new ObjectMapper())
                .apply((GrpcProductGatewayFilterFactory.Config) null);
    }

    @Test
    void shouldAnswerTheProductPageFromGrpc() {
        when(client.findById(eq("prod-1"), any())).thenReturn(Mono.just(
                new ProductView("prod-1", "Phone", "A phone", new BigDecimal("999.90"),
                        "Electronics", 7, true, "2026-01-02T03:04:05", "2026-01-02T03:04:06")));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/prod-1").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        assertTrue(body.contains("\"id\":\"prod-1\""));
        assertTrue(body.contains("\"name\":\"Phone\""));
        assertTrue(body.contains("\"price\":999.90"));
        assertTrue(body.contains("\"active\":true"));
    }

    @Test
    void shouldRelayTheCallerToken() {
        when(client.findById(anyString(), any())).thenReturn(Mono.just(view()));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/prod-1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-value")
                .build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        verify(client).findById("prod-1", "Bearer token-value");
    }

    @Test
    void shouldSendNoTokenForAnAnonymousVisitor() {
        when(client.findById(anyString(), any())).thenReturn(Mono.just(view()));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/prod-1").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        verify(client).findById("prod-1", null);
    }

    /**
     * The list, the search, the category and the price queries are separate endpoints: proxying
     * them is what keeps the pagination and the query parameters working.
     */
    @Test
    void shouldProxyEverythingThatIsNotAProductId() {
        for (String path : new String[]{
                "/api/products",
                "/api/products/active",
                "/api/products/search",
                "/api/products/price-range",
                "/api/products/category/Electronics"}) {
            GatewayFilterChain chain = chain();
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get(path).build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain).filter(exchange);
        }
        verify(client, never()).findById(anyString(), any());
    }

    @Test
    void shouldProxyWrites() {
        GatewayFilterChain chain = chain();
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/products/prod-1").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(client, never()).findById(anyString(), any());
    }

    @Test
    void shouldProxyAPathWithExtraSegments() {
        GatewayFilterChain chain = chain();
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/products/prod-1/reviews").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void shouldMapNotFoundTo404() {
        when(client.findById(anyString(), any())).thenReturn(Mono.error(
                Status.NOT_FOUND.withDescription("Product not found with id: prod-1").asRuntimeException()));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/prod-1").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        assertTrue(body.contains("Product not found"));
    }

    /**
     * An unreachable catalogue is not a missing product: a 404 would be cached and the page would
     * stay broken after product-service came back.
     */
    @Test
    void shouldMapUnavailableTo503() {
        when(client.findById(anyString(), any())).thenReturn(Mono.error(
                Status.UNAVAILABLE.asRuntimeException()));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/prod-1").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldMapDeadlineExceededTo503() {
        when(client.findById(anyString(), any())).thenReturn(Mono.error(
                Status.DEADLINE_EXCEEDED.asRuntimeException()));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/prod-1").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldMapInvalidArgumentTo400() {
        when(client.findById(anyString(), any())).thenReturn(Mono.error(
                Status.INVALID_ARGUMENT.withDescription("bad id").asRuntimeException()));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/prod-1").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldMapAnUnexpectedGrpcFailureTo502() {
        when(client.findById(anyString(), any())).thenReturn(Mono.error(
                Status.INTERNAL.asRuntimeException()));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/prod-1").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
    }

    /** The response is JSON with a content type a browser can render. */
    @Test
    void shouldAnswerWithJson() {
        when(client.findById(anyString(), any())).thenReturn(Mono.just(view()));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/prod-1").build());

        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    void shouldNotCallTheCatalogueForTheBarePrefix() {
        GatewayFilterChain chain = chain();
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(client, never()).findById(anyString(), any());
    }

    private static ProductView view() {
        return new ProductView("prod-1", "Phone", "A phone", new BigDecimal("1.00"),
                "Electronics", 1, true, null, null);
    }

    private static MockServerWebExchange exchange(org.springframework.mock.http.server.reactive.MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    private static GatewayFilterChain chain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        return chain;
    }
}
