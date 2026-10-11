package com.orderplatform.order.config;

import com.orderplatform.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SecurityConfig.
 */
class SecurityConfigTest {

    @Test
    void shouldCreatePasswordEncoder() {
        SecurityConfig config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        assertNotNull(encoder);
        assertTrue(encoder.matches("password", encoder.encode("password")));
    }

    @Test
    void shouldCreateSecurityFilterChain() throws Exception {
        SecurityConfig config = new SecurityConfig();
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_SELF);
        JwtAuthenticationFilter jwtFilter = mock(JwtAuthenticationFilter.class);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        when(http.build()).thenReturn(chain);

        SecurityFilterChain result = config.securityFilterChain(http, jwtFilter);

        assertNotNull(result);
        verify(http).csrf(any());
        verify(http).authorizeHttpRequests(any());
    }

    @Test
    void shouldCreateRestTemplate() {
        SecurityConfig config = new SecurityConfig();
        RestTemplate restTemplate = config.restTemplate();
        assertNotNull(restTemplate);
    }

    /**
     * inventory-service requires an administrator or a manager role for its reserve/release
     * endpoints, so the operator's token has to travel with the outbound call.
     */
    @Test
    void shouldRelayAuthorizationHeaderToDownstreamServices() throws Exception {
        SecurityConfig config = new SecurityConfig();
        RestTemplate restTemplate = config.restTemplate();
        assertEquals(1, restTemplate.getInterceptors().size());

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer operator-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        try {
            MockClientHttpRequest request = new MockClientHttpRequest();
            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
            when(execution.execute(any(), any()))
                    .thenReturn(new MockClientHttpResponse(new byte[0], HttpStatus.OK));

            restTemplate.getInterceptors().get(0).intercept(request, new byte[0], execution);

            assertEquals("Bearer operator-token",
                    request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void shouldNotFailWhenRelayingOutsideOfRequest() throws Exception {
        SecurityConfig config = new SecurityConfig();
        RestTemplate restTemplate = config.restTemplate();
        RequestContextHolder.resetRequestAttributes();

        MockClientHttpRequest request = new MockClientHttpRequest();
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any()))
                .thenReturn(new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        restTemplate.getInterceptors().get(0).intercept(request, new byte[0], execution);

        assertNull(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }
}