package com.smartmed.controller;

import com.smartmed.dto.request.CreateCareRelationshipRequest;
import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.CareRelationshipResponse;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.CareRelationshipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/relationships")
public class CareRelationshipController {

    private final CareRelationshipService relationshipService;

    public CareRelationshipController(CareRelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CareRelationshipResponse>> create(
            @Valid @RequestBody CreateCareRelationshipRequest request,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(relationshipService.create(request, principal)));
    }

    @GetMapping
    public ApiResponse<List<CareRelationshipResponse>> list(
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(relationshipService.list(principal));
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<CareRelationshipResponse> accept(
            @PathVariable Long id,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(relationshipService.accept(id, principal));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<CareRelationshipResponse> reject(
            @PathVariable Long id,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(relationshipService.reject(id, principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id,
                                       @AuthenticationPrincipal SmartMedUserDetails principal) {
        relationshipService.revoke(id, principal);
        return ResponseEntity.noContent().build();
    }
}
