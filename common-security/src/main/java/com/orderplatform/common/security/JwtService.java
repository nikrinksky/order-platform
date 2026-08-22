package com.orderplatform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.List;

/**
 * JWT service for token validation in downstream microservices.
 * Unlike auth-service's JwtService, this one only validates tokens
 * (no generation) and extracts claims: userId, roles, token type.
 */
@Service
public class JwtService {

    /** Claim name for token type ("access" or "refresh"). */
    public static final String TOKEN_TYPE_CLAIM = "token_use";

    /** Claim name for user ID. */
    public static final String USER_ID_CLAIM = "userId";

    /** Claim name for user roles list. */
    public static final String ROLES_CLAIM = "roles";

    @Value("${jwt.secret}")
    private String secret;

    /**
     * Parses a JWT token and returns all claims.
     *
     * @param token JWT token
     * @return claims
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Checks if a token is a valid (non-expired) access token.
     *
     * @param token JWT token
     * @return true if valid access token
     */
    public boolean isValidAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            if (!"access".equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                return false;
            }
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts user ID from token.
     *
     * @param token JWT token
     * @return user ID
     */
    public String extractUserId(String token) {
        return parseToken(token).get(USER_ID_CLAIM, String.class);
    }

    /**
     * Extracts username (email) from token subject.
     *
     * @param token JWT token
     * @return username
     */
    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * Extracts roles list from token.
     *
     * @param token JWT token
     * @return list of role names
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        List<String> roles = parseToken(token).get(ROLES_CLAIM, List.class);
        return roles != null ? roles : List.of();
    }

    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }
}
