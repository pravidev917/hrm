
package com.hrm.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    // Generate JWT token
    public String generateToken(
            String email,
            String role,
            Long userId) {

        Date now = new Date();

        return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .claim("userId", userId)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiration))
            .signWith(getSigningKey())
            .compact();
    }

    // Extract email from token
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Extract role from token
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    // Extract user ID from token
    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    // Validate token
    public boolean isTokenValid(String token, String email) {
        try {
            return extractEmail(token).equals(email)
                && !isTokenExpired(token);
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(
            String token,
            Function<Claims, T> claimsResolver) {

        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
