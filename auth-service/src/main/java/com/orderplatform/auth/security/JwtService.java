/**
 * Service for JWT token operations.
 * Generates, validates, and extracts information from JWT tokens.
 */
package com.orderplatform.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for JWT token operations.
 * Generates, validates, and extracts information from JWT tokens.
 */
@Service
public class JwtService {

    /**
     * Secret key for JWT signing.
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * Access token expiration time in milliseconds.
     */
    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * Refresh token expiration time in milliseconds.
     */
    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    /**
     * Extracts username from JWT token.
     *
     * @param token JWT token
     * @return username (email)
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a claim from JWT token using a resolver function.
     *
     * @param token JWT token
     * @param claimsResolver function to extract the claim
     * @param <T> claim type
     * @return extracted claim
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generates a JWT token for user details.
     *
     * @param userDetails user details
     * @return generated JWT token
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a JWT token with extra claims.
     *
     * @param extraClaims additional claims to include
     * @param userDetails user details
     * @return generated JWT token
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a JWT refresh token.
     *
     * @param userDetails user details
     * @return generated refresh token
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Checks if a JWT token is valid for user details.
     *
     * @param token JWT token
     * @param userDetails user details
     * @return true if token is valid
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /**
     * Checks if a JWT token is expired.
     *
     * @param token JWT token
     * @return true if token is expired
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts expiration date from JWT token.
     *
     * @param token JWT token
     * @return expiration date
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts all claims from JWT token.
     *
     * @param token JWT token
     * @return all claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Gets the signing key for JWT.
     *
     * @return signing key
     */
    private Key getSignInKey() {
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Gets expiration time from token.
     *
     * @param token JWT token
     * @return expiration time in milliseconds
     */
    public long getExpirationFromToken(String token) {
        return extractExpiration(token).getTime();
    }

    /**
     * Gets refresh token expiration.
     *
     * @return refresh token expiration in milliseconds
     */
    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    /**
     * Checks if a refresh token is valid.
     *
     * @param token refresh token
     * @return true if refresh token is valid
     */
    public boolean isRefreshTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
