package com.smartmed.controller;

import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.PatientAnalyticsResponse;
import com.smartmed.dto.response.PatientMonitoringSummaryResponse;
import com.smartmed.dto.response.MonitoringDoseResponse;
import com.smartmed.entity.RelationshipType;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.MonitoringService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/caregiver/patients")
public class CaregiverController {

    private final MonitoringService monitoringService;

    public CaregiverController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping
    public ApiResponse<List<PatientMonitoringSummaryResponse>> patients(
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(monitoringService.patients(principal, RelationshipType.CAREGIVER));
    }

    @GetMapping("/{patientId}/today")
    public ApiResponse<List<MonitoringDoseResponse>> today(
            @PathVariable Long patientId,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(monitoringService.today(principal, patientId, RelationshipType.CAREGIVER));
    }

    @GetMapping("/{patientId}/analytics")
    public ApiResponse<PatientAnalyticsResponse> analytics(
            @PathVariable Long patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(monitoringService.analytics(principal, patientId, from, to, RelationshipType.CAREGIVER));
    }
}
