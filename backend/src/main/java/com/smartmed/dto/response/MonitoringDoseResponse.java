package com.smartmed.dto.response;

import com.smartmed.entity.DoseStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record MonitoringDoseResponse(
        Long doseId,
        String medicationName,
        LocalDate scheduledDate,
        LocalTime scheduledTime,
        DoseStatus status,
        Instant takenAt
) {
    public static MonitoringDoseResponse from(DoseResponse dose) {
        return new MonitoringDoseResponse(
                dose.id(), dose.medicationName(), dose.scheduledDate(),
                dose.scheduledTime(), dose.status(), dose.takenAt()
        );
    }
}
