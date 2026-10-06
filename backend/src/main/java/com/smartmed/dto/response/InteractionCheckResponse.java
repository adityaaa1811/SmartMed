package com.smartmed.dto.response;

import java.util.List;

public record InteractionCheckResponse(
        InteractionCheckStatus status,
        int checkedMedicationCount,
        String providerId,
        List<InteractionResponse> interactions
) {
    public InteractionCheckResponse {
        interactions = List.copyOf(interactions);
    }
}
