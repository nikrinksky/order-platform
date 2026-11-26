package com.orderplatform.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.gateway.client.ProductCatalogClient;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Serves {@code GET /api/products/{id}} from gRPC instead of proxying it.
 *
 * <p>It is a route filter rather than a controller on purpose: a controller would bypass the
 * gateway's own filters, and the product page is the most frequently requested path, so losing the
 * rate limiter and the authentication filter for it would be a regression. Being a filter, this
 * runs after them - {@code ORDER} is one greater than the rate limiter's.
 *
 * <p>Everything else under {@code /api/products} is left to the normal proxy: the list, the
 * search, the category and price queries and every write keep their REST contract, and the gateway
 * does not have to model a request body it does not use.
 */
@Component
public class GrpcProductGatewayFilterFactory
        extends AbstractGatewayFilterFactory<GrpcProductGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(GrpcProductGatewayFilterFactory.class);

    /** Prefix of the catalogue path. */
    private static final String PRODUCTS_PREFIX = "/api/products/";

    /**
     * Sub-paths that are not a product id. They are served by their own REST endpoints and must
     * reach product-service unchanged.
     */
    private static final Set<String> RESERVED_SEGMENTS = Set.of("active", "search", "price-range", "category");

    /**
     * Runs after the rate limiter (which is not ordered and therefore sorts first) so a client
     * over its limit is rejected before a call is made to product-service.
     */
    private static final int ORDER = 1;

    private final ProductCatalogClient productCatalogClient;
    private final ObjectMapper objectMapper;

    public GrpcProductGatewayFilterFactory(ProductCatalogClient productCatalogClient,
                                           ObjectMapper objectMapper) {
        super(Config.class);
        this.productCatalogClient = productCatalogClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new OrderedGatewayFilter(this::filter, ORDER);
    }

    private Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String productId = productIdOf(exchange.getRequest());
        if (productId == null) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return productCatalogClient.findById(productId, authorization)
                .flatMap(product -> writeJson(exchange, HttpStatus.OK, product))
                .onErrorResume(StatusRuntimeException.class, error -> writeGrpcError(exchange, productId, error))
                .onErrorResume(JsonProcessingException.class, error -> {
                    log.error("Cannot serialise the product {} for the response", productId, error);
                    return writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                            "Internal Server Error", "Cannot write the response");
                });
    }

    /**
     * Extracts the product id when the request is exactly one segment under the catalogue root.
     *
     * @param request incoming request
     * @return the id, or null when the request must be proxied as before
     */
    private static String productIdOf(ServerHttpRequest request) {
        if (!"GET".equals(request.getMethod().name())) {
            return null;
        }
        String path = request.getURI().getPath();
        if (!path.startsWith(PRODUCTS_PREFIX)) {
            return null;
        }
        String segment = path.substring(PRODUCTS_PREFIX.length());
        if (segment.isEmpty() || segment.indexOf('/') >= 0 || RESERVED_SEGMENTS.contains(segment)) {
            return null;
        }
        return segment;
    }

    /** Maps a gRPC status to the HTTP status a REST client would have received. */
    private Mono<Void> writeGrpcError(ServerWebExchange exchange, String productId,
                                      StatusRuntimeException error) {
        Status.Code code = error.getStatus().getCode();
        String description = error.getStatus().getDescription() == null ?
                "Product lookup failed" :
                error.getStatus().getDescription();
        if (code == Status.Code.NOT_FOUND) {
            return writeError(exchange, HttpStatus.NOT_FOUND, "Not Found", description);
        }
        if (code == Status.Code.INVALID_ARGUMENT) {
            return writeError(exchange, HttpStatus.BAD_REQUEST, "Bad Request", description);
        }
        if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
            log.warn("product-service is not reachable while reading {}: {}", productId, code);
            return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                    "Catalogue is unavailable");
        }
        log.error("gRPC product lookup failed for {} with {}", productId, code, error);
        return writeError(exchange, HttpStatus.BAD_GATEWAY, "Bad Gateway", "Catalogue call failed");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status,
                                  String error, String message) {
        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", error,
                "message", message);
        return writeJson(exchange, status, body);
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, Object body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /** No settings yet; the class is required by {@link AbstractGatewayFilterFactory}. */
    public static class Config {
    }
}
