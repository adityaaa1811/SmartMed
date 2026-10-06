package com.smartmed.controller;

import com.smartmed.dto.request.InteractionCheckRequest;
import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.InteractionCheckResponse;
import com.smartmed.dto.response.InteractionCheckStatus;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.InteractionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interactions")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<InteractionCheckResponse>> check(
            @Valid @RequestBody InteractionCheckRequest request,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        InteractionCheckResponse result = interactionService.check(request, principal);
        if (result.status() == InteractionCheckStatus.PROVIDER_UNAVAILABLE) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failureWithData("INTERACTION_PROVIDER_UNAVAILABLE",
                            "Interaction checking is currently unavailable", result));
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
