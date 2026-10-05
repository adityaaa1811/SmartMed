package com.smartmed.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateMedicationRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 120) String dosage,
        @NotBlank @Size(max = 120) String frequency,
        @Size(max = 2000) String instructions,
        @NotNull LocalDate startDate,
        LocalDate endDate
) {
    @AssertTrue(message = "endDate cannot be before startDate")
    public boolean isEndDateValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
