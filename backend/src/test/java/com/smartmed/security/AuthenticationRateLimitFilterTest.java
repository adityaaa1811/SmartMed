package com.smartmed.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.config.SmartMedProperties;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationRateLimitFilterTest {
    @Test
    void registrationLimitReturns429AndExpires() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SmartMedProperties properties = new SmartMedProperties();
        properties.getSecurity().getRateLimit().setRegistrationRequests(2);
        properties.getSecurity().getRateLimit().setWindowSeconds(30);
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(properties, new ObjectMapper(), clock);

        assertThat(send(filter, "/api/v1/auth/register", 201).getStatus()).isEqualTo(201);
        assertThat(send(filter, "/api/v1/auth/register", 201).getStatus()).isEqualTo(201);
        MockHttpServletResponse limited = send(filter, "/api/v1/auth/register", 201);
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(limited.getContentAsString()).contains("TOO_MANY_REQUESTS").doesNotContain("email");

        clock.advanceSeconds(31);
        assertThat(send(filter, "/api/v1/auth/register", 201).getStatus()).isEqualTo(201);
    }

    @Test
    void successfulLoginClearsFailureWindowAndFailuresAreLimitedByRemoteAddress() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SmartMedProperties properties = new SmartMedProperties();
        properties.getSecurity().getRateLimit().setLoginFailures(2);
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(properties, new ObjectMapper(), clock);
        assertThat(send(filter, "/api/v1/auth/login", 401).getStatus()).isEqualTo(401);
        assertThat(send(filter, "/api/v1/auth/login", 200).getStatus()).isEqualTo(200);
        assertThat(send(filter, "/api/v1/auth/login", 401).getStatus()).isEqualTo(401);
        assertThat(send(filter, "/api/v1/auth/login", 401).getStatus()).isEqualTo(401);
        assertThat(send(filter, "/api/v1/auth/login", 401).getStatus()).isEqualTo(429);
    }

    private static MockHttpServletResponse send(AuthenticationRateLimitFilter filter, String path, int downstreamStatus)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(path);
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ServletRequest req, ServletResponse res) ->
                ((MockHttpServletResponse) res).setStatus(downstreamStatus));
        return response;
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        MutableClock(Instant initial) { current = new AtomicReference<>(initial); }
        void advanceSeconds(long seconds) { current.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current.get(); }
    }
}
