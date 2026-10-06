package com.smartmed.dto.response;

import com.smartmed.entity.User;

import java.math.BigDecimal;

public record PatientMonitoringSummaryResponse(
        Long patientId,
        String patientName,
        String patientEmail,
        BigDecimal adherencePercentage,
        long taken,
        long missed,
        long skipped,
        long pending
) {
    public static PatientMonitoringSummaryResponse from(User patient, AnalyticsSummaryResponse summary) {
        return new PatientMonitoringSummaryResponse(
                patient.getId(), patient.getFullName(), patient.getEmail(),
                summary.adherencePercentage(), summary.taken(), summary.missed(),
                summary.skipped(), summary.pending()
        );
    }
}
