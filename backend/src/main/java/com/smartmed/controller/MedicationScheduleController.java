package com.smartmed.controller;

import com.smartmed.dto.request.CreateScheduleRequest;
import com.smartmed.dto.request.UpdateScheduleRequest;
import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.ScheduleResponse;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.MedicationScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MedicationScheduleController {

    private final MedicationScheduleService scheduleService;

    public MedicationScheduleController(MedicationScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping("/api/v1/medications/{medicationId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResponse> create(@PathVariable Long medicationId,
                                                @Valid @RequestBody CreateScheduleRequest request,
                                                @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(scheduleService.create(medicationId, request, principal));
    }

    @GetMapping("/api/v1/medications/{medicationId}/schedules")
    public ApiResponse<List<ScheduleResponse>> list(@PathVariable Long medicationId,
                                                    @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(scheduleService.list(medicationId, principal));
    }

    @GetMapping("/api/v1/schedules/{scheduleId}")
    public ApiResponse<ScheduleResponse> get(@PathVariable Long scheduleId,
                                             @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(scheduleService.get(scheduleId, principal));
    }

    @PutMapping("/api/v1/schedules/{scheduleId}")
    public ApiResponse<ScheduleResponse> update(@PathVariable Long scheduleId,
                                               @Valid @RequestBody UpdateScheduleRequest request,
                                               @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(scheduleService.update(scheduleId, request, principal));
    }

    @DeleteMapping("/api/v1/schedules/{scheduleId}")
    public ResponseEntity<Void> delete(@PathVariable Long scheduleId,
                                       @AuthenticationPrincipal SmartMedUserDetails principal) {
        scheduleService.delete(scheduleId, principal);
        return ResponseEntity.noContent().build();
    }
}
