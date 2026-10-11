package com.smartmed.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RefillMedicationRequest(
        @NotNull @Positive(message = "Refill amount must be greater than zero") Long amount
) {
    @JsonCreator
    public RefillMedicationRequest(@JsonProperty("amount") Long amount) {
        this.amount = amount;
    }
}
