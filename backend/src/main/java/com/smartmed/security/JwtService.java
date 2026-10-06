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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Arrays;

@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final SmartMedProperties properties;
    private final Environment environment;
    private SecretKey signingKey;

    public JwtService(SmartMedProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
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
        String normalized = secret.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("replace-with") || normalized.contains("change-me")
                || normalized.contains("change_me") || normalized.contains("placeholder")
                || normalized.contains("example") || normalized.contains("your-secret")
                || normalized.contains("your-local-secret") || normalized.contains("sample-secret")
                || normalized.contains("demo-secret")) {
            throw new JwtConfigurationException("JWT secret is a placeholder. Configure SMARTMED_JWT_SECRET with a generated secret.");
        }
        if (environment.acceptsProfiles(Profiles.of("prod")) && !secret.startsWith("base64:")) {
            throw new JwtConfigurationException("Production requires base64: plus at least 32 random bytes in SMARTMED_JWT_SECRET.");
        }
        if (secret.length() < 32) {
            throw new JwtConfigurationException(
                    "JWT secret is too short. SMARTMED_JWT_SECRET must be at least 32 characters."
            );
        }
        byte[] keyBytes;
        try {
            keyBytes = secret.startsWith("base64:")
                    ? Decoders.BASE64.decode(secret.substring("base64:".length()))
                    : secret.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new JwtConfigurationException("SMARTMED_JWT_SECRET must contain valid base64 key material.");
        }
        if (keyBytes.length < 32) {
            throw new JwtConfigurationException(
                    "JWT secret must provide at least 256 bits (32 bytes) of key material."
            );
        }
        if (isObviousPattern(keyBytes)) {
            throw new JwtConfigurationException("JWT secret contains an obvious low-entropy pattern.");
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
            return subject != null && userDetails != null
                    && subject.equals(String.valueOf(userDetails.getId()))
                    && !isTokenExpired(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public SmartMedUserDetails validateTokenAndLoadUser(String token, CustomUserDetailsService userDetailsService) {
        Claims claims = parseClaims(token);
        if (claims.getExpiration() == null) {
            throw new JwtException("Invalid token claims");
        }
        if (isTokenExpired(claims)) {
            throw new ExpiredJwtException(null, claims, "JWT expired");
        }
        String email = claims.get("email", String.class);
        String subject = claims.getSubject();
        if (email == null || email.isBlank() || subject == null || subject.isBlank()) {
            throw new JwtException("Invalid token claims");
        }
        SmartMedUserDetails userDetails;
        try {
            userDetails = (SmartMedUserDetails) userDetailsService.loadUserByUsername(email);
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            throw new JwtException("Invalid token account", ex);
        }
        if (!subject.equals(String.valueOf(userDetails.getId()))) {
            throw new JwtException("Invalid token subject");
        }
        return userDetails;
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
        return expiration == null || !expiration.after(new Date());
    }

    private static boolean isObviousPattern(byte[] bytes) {
        if (Arrays.stream(toUnsignedInts(bytes)).distinct().count() == 1) return true;

        for (int period = 1; period <= Math.min(16, bytes.length / 2); period++) {
            boolean repeats = true;
            for (int index = period; index < bytes.length; index++) {
                if (bytes[index] != bytes[index % period]) {
                    repeats = false;
                    break;
                }
            }
            if (repeats) return true;
        }

        int firstDelta = bytes.length > 1 ? bytes[1] - bytes[0] : 0;
        if (firstDelta != 0 && bytes.length >= 8) {
            boolean sequence = true;
            for (int index = 2; index < bytes.length; index++) {
                if (bytes[index] - bytes[index - 1] != firstDelta) {
                    sequence = false;
                    break;
                }
            }
            if (sequence) return true;
        }
        return false;
    }

    private static int[] toUnsignedInts(byte[] bytes) {
        int[] values = new int[bytes.length];
        for (int index = 0; index < bytes.length; index++) values[index] = Byte.toUnsignedInt(bytes[index]);
        return values;
    }
}
