package com.orderplatform.order.config;

import com.orderplatform.common.security.JwtAuthenticationFilter;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The real registry when Spring provides one; the no-op default keeps the class usable from a
     * plain unit test that instantiates it directly.
     */
    @Autowired(required = false)
    private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

    /**
     * Paths the API documentation is served from: the OpenAPI document, the Swagger UI page and
     * its static resources. Public on purpose - a browser opens the UI without a token, and the
     * document itself carries no data, only the shape of the API.
     */
    private static final String[] DOCS_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**"
    };

    /**
     * Disabled when {@code spring.security.enabled=false}, which the integration test profile sets
     * so that {@code TestSecurityConfig} can supply its permit-all chain instead. The condition is
     * on the bean rather than the class on purpose: {@link #restTemplate()} is still needed for the
     * catalogue lookup in that profile.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.security.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                  JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // API documentation: the document and the UI are read by a browser
                        // without a token, so they cannot be behind the bearer check
                        .requestMatchers(DOCS_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The client order-service uses to read the catalogue.
     *
     * <p>It relays the caller's {@code Authorization} header, so the catalogue call carries the
     * identity of the operator as well. A call made outside a request (no header to relay) is left
     * unauthenticated and is refused downstream.
     *
     * <p>The registry makes each call a span of its own and puts the trace context into the
     * outgoing headers, so a checkout and the calls it triggers are one trace. Stock operations no
     * longer use this bean: they run over gRPC, and {@code JwtClientInterceptor} relays the token
     * there.
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(this::relayAuthorizationHeader));
        restTemplate.setObservationRegistry(observationRegistry);
        return restTemplate;
    }

    private ClientHttpResponse relayAuthorizationHeader(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
        return execution.execute(request, body);
    }
}
