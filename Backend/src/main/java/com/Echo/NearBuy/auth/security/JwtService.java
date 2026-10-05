package com.Echo.NearBuy.auth.security;

import com.Echo.NearBuy.common.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long expirationMillis;
    private final long refreshExpirationMillis;

    public JwtService(
            @Value("${app.jwt.secret:}") String base64Secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMillis,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMillis) {
        if (base64Secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured as a Base64-encoded key");
        }
        if (expirationMillis <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_MS must be greater than zero");
        }
        if (refreshExpirationMillis <= 0) {
            throw new IllegalStateException("JWT_REFRESH_EXPIRATION_MS must be greater than zero");
        }

        try {
            this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64 and contain at least 32 bytes", exception);
        }
        this.expirationMillis = expirationMillis;
        this.refreshExpirationMillis = refreshExpirationMillis;
    }

    public String generateToken(Long userId, String email, Role role, String tokenType) {
        long lifetimeMillis = switch (tokenType) {
            case "access" -> expirationMillis;
            case "refresh" -> refreshExpirationMillis;
            default -> throw new IllegalArgumentException("Unsupported JWT token type");
        };
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role.name())
                .claim("tokenType", tokenType)
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(lifetimeMillis)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }

    public Role extractRole(String token) {
        return Role.valueOf(parseToken(token).get("role", String.class));
    }

    public Long extractUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    public String extractTokenType(String token) {
        return parseToken(token).get("tokenType", String.class);
    }

    public boolean validateToken(String token, String expectedTokenType) {
        try {
            Claims claims = parseToken(token);
            String role = claims.get("role", String.class);
            if (claims.getSubject() == null || claims.get("userId", Long.class) == null || role == null) {
                return false;
            }
            Role.valueOf(role);
            return expectedTokenType.equals(claims.get("tokenType", String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
