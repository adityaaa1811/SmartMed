package com.smartmed.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.config.SmartMedProperties;
import com.smartmed.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** A bounded, single-instance limiter for public authentication endpoints. */
@Component
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REGISTER = "/api/v1/auth/register";
    private final SmartMedProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>();

    public AuthenticationRateLimitFilter(SmartMedProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean login = "POST".equals(request.getMethod()) && LOGIN.equals(path);
        boolean register = "POST".equals(request.getMethod()) && REGISTER.equals(path);
        if (!login && !register) {
            chain.doFilter(request, response);
            return;
        }

        String key = (login ? "login:" : "register:") + clientAddress(request);
        int limit = login ? properties.getSecurity().getRateLimit().getLoginFailures()
                : properties.getSecurity().getRateLimit().getRegistrationRequests();
        Instant now = clock.instant();
        synchronized (buckets) {
            evictExpired(now);
            Bucket bucket = buckets.get(key);
            if (bucket != null && bucket.count >= limit) {
                writeTooManyRequests(response, bucket.startedAt.plus(window()));
                return;
            }
            // Reserve the attempt before dispatch so concurrent login bursts cannot all pass the check.
            increment(key, now);
        }

        chain.doFilter(request, response);
        if (login && response.getStatus() >= 200 && response.getStatus() < 300) {
            synchronized (buckets) {
                buckets.remove(key);
            }
        }
    }

    private synchronized void increment(String key, Instant now) {
        synchronized (buckets) {
            Bucket bucket = buckets.get(key);
            if (bucket == null || expired(bucket, now)) {
                if (buckets.size() >= Math.max(1, properties.getSecurity().getRateLimit().getMaxTrackedKeys())) {
                    Iterator<String> keys = buckets.keySet().iterator();
                    if (keys.hasNext()) { keys.next(); keys.remove(); }
                }
                buckets.put(key, new Bucket(now, 1));
            } else {
                bucket.count++;
            }
        }
    }

    private void evictExpired(Instant now) {
        buckets.entrySet().removeIf(entry -> expired(entry.getValue(), now));
    }

    private boolean expired(Bucket bucket, Instant now) {
        return !now.isBefore(bucket.startedAt.plus(window()));
    }

    private Duration window() {
        return Duration.ofSeconds(Math.max(1, properties.getSecurity().getRateLimit().getWindowSeconds()));
    }

    private void writeTooManyRequests(HttpServletResponse response, Instant resetAt) throws IOException {
        long retry = Math.max(1, Duration.between(clock.instant(), resetAt).toSeconds());
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retry));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(
                "TOO_MANY_REQUESTS", "Too many authentication requests. Try again later."));
    }

    private static String clientAddress(HttpServletRequest request) {
        // Do not trust caller-controlled forwarding headers unless a trusted proxy is configured.
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private static final class Bucket {
        private final Instant startedAt;
        private int count;
        private Bucket(Instant startedAt, int count) { this.startedAt = startedAt; this.count = count; }
    }
}
