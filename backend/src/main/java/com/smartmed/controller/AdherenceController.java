package com.smartmed.controller;

import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.DoseResponse;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.AdherenceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/adherence")
public class AdherenceController {

    private final AdherenceService adherenceService;

    public AdherenceController(AdherenceService adherenceService) {
        this.adherenceService = adherenceService;
    }

    @GetMapping("/today")
    public ApiResponse<List<DoseResponse>> today(@AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(adherenceService.today(principal));
    }

    @PostMapping("/{doseId}/taken")
    public ApiResponse<DoseResponse> taken(@PathVariable Long doseId,
                                          @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(adherenceService.markTaken(doseId, principal));
    }

    @PostMapping("/{doseId}/missed")
    public ApiResponse<DoseResponse> missed(@PathVariable Long doseId,
                                            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(adherenceService.markMissed(doseId, principal));
    }

    @PostMapping("/{doseId}/skipped")
    public ApiResponse<DoseResponse> skipped(@PathVariable Long doseId,
                                            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(adherenceService.markSkipped(doseId, principal));
    }

    @GetMapping("/history")
    public ApiResponse<List<DoseResponse>> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(adherenceService.history(from, to, page, pageSize, principal));
    }
}
