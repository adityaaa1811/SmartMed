package com.smartmed.controller;

import com.smartmed.dto.response.AnalyticsSummaryResponse;
import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.DailyAnalyticsResponse;
import com.smartmed.dto.response.MedicationAnalyticsResponse;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ApiResponse<AnalyticsSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(analyticsService.summary(from, to, principal));
    }

    @GetMapping("/daily")
    public ApiResponse<List<DailyAnalyticsResponse>> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(analyticsService.daily(from, to, principal));
    }

    @GetMapping("/medications")
    public ApiResponse<List<MedicationAnalyticsResponse>> medications(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(analyticsService.medications(from, to, principal));
    }
}
