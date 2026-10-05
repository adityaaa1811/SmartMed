package com.smartmed.dto.response;

import com.smartmed.entity.Medication;

import java.time.Instant;
import java.time.LocalDate;

public record MedicationResponse(
        Long id,
        String name,
        String dosage,
        String frequency,
        String instructions,
        LocalDate startDate,
        LocalDate endDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static MedicationResponse from(Medication medication) {
        return new MedicationResponse(
                medication.getId(), medication.getName(), medication.getDosage(),
                medication.getFrequency(), medication.getInstructions(), medication.getStartDate(),
                medication.getEndDate(), medication.getCreatedAt(), medication.getUpdatedAt()
        );
    }
}
