package com.smartmed.service.interaction;

public record DrugInteractionResult(
        String medicationA,
        String medicationB,
        InteractionSeverity severity,
        String description,
        String recommendation,
        String sourceReference,
        boolean fromMockProvider
) {
}
