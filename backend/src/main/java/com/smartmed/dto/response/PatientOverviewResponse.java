package com.smartmed.dto.response;

import java.util.List;

public record PatientOverviewResponse(
        Long patientId,
        String patientName,
        String patientEmail,
        List<MedicationResponse> medications,
        List<ScheduleResponse> activeSchedules,
        AnalyticsSummaryResponse adherenceSummary
) {
}
