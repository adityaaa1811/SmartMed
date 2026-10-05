package com.smartmed.controller;

import com.smartmed.config.SmartMedProperties;
import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final SmartMedProperties properties;

    public HealthController(SmartMedProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        HealthResponse body = new HealthResponse(
                "UP",
                "SmartMed",
                properties.getApi().getVersion(),
                Instant.now()
        );
        return ApiResponse.ok(body);
    }
}
