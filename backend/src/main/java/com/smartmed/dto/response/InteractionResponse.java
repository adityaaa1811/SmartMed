package com.smartmed.dto.response;

import com.smartmed.service.interaction.DrugInteractionResult;
import com.smartmed.service.interaction.InteractionSeverity;

public record InteractionResponse(
        String medicationA,
        String medicationB,
        InteractionSeverity severity,
        String description
) {
    public static InteractionResponse from(DrugInteractionResult interaction) {
        return new InteractionResponse(interaction.medicationA(), interaction.medicationB(),
                interaction.severity(), interaction.description());
    }
}
