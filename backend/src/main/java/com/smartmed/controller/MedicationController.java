package com.smartmed.controller;

import com.smartmed.dto.request.CreateMedicationRequest;
import com.smartmed.dto.request.UpdateMedicationRequest;
import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.MedicationResponse;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.MedicationService;
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
@RequestMapping("/api/v1/medications")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MedicationResponse> create(@Valid @RequestBody CreateMedicationRequest request,
                                                   @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(medicationService.create(request, principal));
    }

    @GetMapping
    public ApiResponse<List<MedicationResponse>> list(@AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(medicationService.list(principal));
    }

    @GetMapping("/{id}")
    public ApiResponse<MedicationResponse> get(@PathVariable Long id,
                                                @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(medicationService.get(id, principal));
    }

    @PutMapping("/{id}")
    public ApiResponse<MedicationResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateMedicationRequest request,
                                                    @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(medicationService.update(id, request, principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal SmartMedUserDetails principal) {
        medicationService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
