package com.smartmed.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyAnalyticsResponse(
        LocalDate date,
        long taken,
        long missed,
        long skipped,
        long pending,
        BigDecimal adherencePercentage
) {
}
