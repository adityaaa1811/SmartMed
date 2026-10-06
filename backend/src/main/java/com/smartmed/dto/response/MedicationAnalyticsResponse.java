package com.smartmed.dto.response;

import java.math.BigDecimal;

public record MedicationAnalyticsResponse(
        Long medicationId,
        String medicationName,
        long taken,
        long missed,
        long skipped,
        long pending,
        BigDecimal adherencePercentage
) {
}
