package com.orderplatform.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityHandlerTest {

    @Test
    void customAuthenticationEntryPoint_shouldReturnUnauthorized() throws Exception {
        SecurityConfig.CustomAuthenticationEntryPoint entryPoint =
                new SecurityConfig.CustomAuthenticationEntryPoint();
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));
        AuthenticationException ex = new BadCredentialsException("Bad credentials");

        entryPoint.commence(request, response, ex);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertTrue(writer.toString().contains("Unauthorized"));
        assertTrue(writer.toString().contains("Bad credentials"));
    }

    @Test
    void customAccessDeniedHandler_shouldReturnForbidden() throws Exception {
        SecurityConfig.CustomAccessDeniedHandler handler =
                new SecurityConfig.CustomAccessDeniedHandler();
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        handler.handle(request, response, ex);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertTrue(writer.toString().contains("Forbidden"));
        assertTrue(writer.toString().contains("Access denied"));
    }
}