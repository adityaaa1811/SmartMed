package com.smartmed.dto.response;

import java.util.List;

public record PatientAnalyticsResponse(
        AnalyticsSummaryResponse summary,
        List<DailyAnalyticsResponse> daily,
        List<MedicationAnalyticsResponse> medications
) {
}
