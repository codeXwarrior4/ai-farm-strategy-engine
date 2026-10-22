package com.farmengine.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility for generating and validating JWT tokens.
 *
 * Phase 1 note:
 *   JWT tokens are scaffolded here but not enforced on profile endpoints.
 *   Token-based access control will be applied in Phase 2 when the full
 *   authentication flow (register / login) is wired to the frontend.
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generate a signed JWT for the given username and optional userId.
     */
    public String generateToken(String username, Long userId) {
        var builder = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey);

        if (userId != null) {
            builder.claim("userId", userId);
        }

        return builder.compact();
    }

    /**
     * Generate a signed JWT for the given username.
     */
    public String generateToken(String username) {
        return generateToken(username, null);
    }

    /**
     * Generate an already-expired JWT for testing rejection behavior.
     */
    public String generateExpiredToken(String username, Long userId) {
        var builder = Jwts.builder()
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis() - 100_000))
                .expiration(new Date(System.currentTimeMillis() - 10_000))
                .signWith(secretKey);

        if (userId != null) {
            builder.claim("userId", userId);
        }

        return builder.compact();
    }

    /**
     * Extract the username (subject) from a token.
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extract the userId claim from a token, if present.
     */
    public Long extractUserId(String token) {
        Object id = parseClaims(token).get("userId");
        if (id instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /**
     * Validate a token: signature, expiry, and subject presence.
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject() != null && !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
