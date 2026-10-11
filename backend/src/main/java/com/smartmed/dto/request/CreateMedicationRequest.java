package com.smartmed.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record CreateMedicationRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 120) String dosage,
        @NotBlank @Size(max = 120) String frequency,
        @Size(max = 2000) String instructions,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @PositiveOrZero(message = "Quantity cannot be negative") long quantity,
        @Size(max = 20) String unit,
        @PositiveOrZero(message = "Low stock threshold cannot be negative") Long lowStockThreshold
) {
    @JsonCreator
    public CreateMedicationRequest(
            @JsonProperty("name") @NotBlank @Size(max = 160) String name,
            @JsonProperty("dosage") @NotBlank @Size(max = 120) String dosage,
            @JsonProperty("frequency") @NotBlank @Size(max = 120) String frequency,
            @JsonProperty("instructions") @Size(max = 2000) String instructions,
            @JsonProperty("startDate") @NotNull LocalDate startDate,
            @JsonProperty("endDate") LocalDate endDate,
            @JsonProperty("quantity") @PositiveOrZero(message = "Quantity cannot be negative") Long quantity,
            @JsonProperty("unit") @Size(max = 20) String unit,
            @JsonProperty("lowStockThreshold") @PositiveOrZero(message = "Low stock threshold cannot be negative") Long lowStockThreshold) {
        this(name, dosage, frequency, instructions, startDate, endDate,
                quantity != null ? quantity : 0L,
                unit,
                lowStockThreshold);
    }

    @AssertTrue(message = "endDate cannot be before startDate")
    public boolean isEndDateValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
