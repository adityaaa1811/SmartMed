package com.smartmed.security;

import com.smartmed.config.SmartMedProperties;
import com.smartmed.entity.Role;
import com.smartmed.exception.JwtConfigurationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final SmartMedProperties properties;
    private SecretKey signingKey;

    public JwtService(SmartMedProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initSigningKey() {
        String secret = properties.getSecurity().getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new JwtConfigurationException(
                    "JWT secret is not configured. Set environment variable SMARTMED_JWT_SECRET "
                            + "(minimum 32 characters for HS256)."
            );
        }
        if (secret.length() < 32) {
            throw new JwtConfigurationException(
                    "JWT secret is too short. SMARTMED_JWT_SECRET must be at least 32 characters."
            );
        }
        byte[] keyBytes = secret.startsWith("base64:")
                ? Decoders.BASE64.decode(secret.substring("base64:".length()))
                : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new JwtConfigurationException(
                    "JWT secret must provide at least 256 bits (32 bytes) of key material."
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, String email, Role role) {
        long expirationMs = properties.getSecurity().getJwt().getExpirationMs();
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationMs() {
        return properties.getSecurity().getJwt().getExpirationMs();
    }

    public boolean isTokenValid(String token, SmartMedUserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            String subject = claims.getSubject();
            return subject.equals(String.valueOf(userDetails.getId()))
                    && !isTokenExpired(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public SmartMedUserDetails validateTokenAndLoadUser(String token, CustomUserDetailsService userDetailsService) {
        Claims claims = parseClaims(token);
        if (isTokenExpired(claims)) {
            throw new ExpiredJwtException(null, claims, "JWT expired");
        }
        String email = claims.get("email", String.class);
        if (email == null || email.isBlank()) {
            throw new JwtException("Invalid token claims");
        }
        return (SmartMedUserDetails) userDetailsService.loadUserByUsername(email);
    }

    Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }
}
