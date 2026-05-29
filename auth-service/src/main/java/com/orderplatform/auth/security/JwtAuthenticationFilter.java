package com.orderplatform.auth.security;

import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import com.orderplatform.auth.service.CustomUserDetailsService;
import com.orderplatform.auth.service.TokenBlacklistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Проверка черного списка
                if (tokenBlacklistService.isTokenBlacklisted(jwt)) {
                    log.warn("Token is blacklisted for user: {}", userEmail);
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), "Token has been revoked");
                    return;
                }

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

//                // Проверка активен ли пользователь(временно закомментирован)
                User user = userRepository.findByEmail(userEmail).orElse(null);
//                if (user == null || !user.isActive()) {
//                    log.warn("User is not active: {}", userEmail);
//                    response.sendError(HttpStatus.UNAUTHORIZED.value(), "User account is disabled");
//                    return;
//                }

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.warn("JWT validation error: {}", e.getMessage());
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token");
        } catch (Exception e) {
            log.error("Unexpected error in JWT filter: {}", e.getMessage());
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Authentication error");
        }
    }
}