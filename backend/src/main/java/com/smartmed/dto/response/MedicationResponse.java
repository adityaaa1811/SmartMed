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
        Instant updatedAt,
        Long quantity,
        String unit,
        Long lowStockThreshold,
        boolean lowStock
) {
    public static MedicationResponse from(Medication medication) {
        boolean lowStock = medication.getLowStockThreshold() != null
                && medication.getLowStockThreshold() > 0
                && medication.getQuantity() != null
                && medication.getQuantity() <= medication.getLowStockThreshold();
        return new MedicationResponse(
                medication.getId(), medication.getName(), medication.getDosage(),
                medication.getFrequency(), medication.getInstructions(), medication.getStartDate(),
                medication.getEndDate(), medication.getCreatedAt(), medication.getUpdatedAt(),
                medication.getQuantity(), medication.getUnit(),
                medication.getLowStockThreshold(), lowStock
        );
    }
}
