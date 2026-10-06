package com.smartmed.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnalyticsSummaryResponse(
        LocalDate from,
        LocalDate to,
        long totalDoses,
        long taken,
        long missed,
        long skipped,
        long pending,
        BigDecimal adherencePercentage
) {
}
