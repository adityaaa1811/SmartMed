package com.smartmed.dto.response;

import java.time.Instant;

public record HealthResponse(
        String status,
        String application,
        String apiVersion,
        Instant timestamp
) {
}
