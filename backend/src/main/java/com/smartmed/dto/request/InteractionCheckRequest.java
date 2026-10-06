package com.smartmed.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

public record InteractionCheckRequest(
        @NotEmpty(message = "Select at least one medication")
        @Size(max = 20, message = "Select no more than 20 medications")
        List<@NotNull(message = "Medication IDs cannot be null") @Positive(message = "Medication IDs must be positive") Long> medicationIds
) {
    @AssertTrue(message = "Medication IDs must not contain duplicates")
    public boolean hasUniqueMedicationIds() {
        return medicationIds == null || new HashSet<>(medicationIds).size() == medicationIds.size();
    }
}
