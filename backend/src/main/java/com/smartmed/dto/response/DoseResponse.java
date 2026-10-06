package com.smartmed.dto.response;

import com.smartmed.entity.DoseRecord;
import com.smartmed.entity.DoseStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record DoseResponse(
        Long id,
        Long scheduleId,
        Long medicationId,
        String medicationName,
        String dosage,
        LocalDate scheduledDate,
        LocalTime scheduledTime,
        DoseStatus status,
        Instant takenAt
) {
    public static DoseResponse from(DoseRecord dose) {
        var medication = dose.getSchedule().getMedication();
        return new DoseResponse(
                dose.getId(), dose.getSchedule().getId(), medication.getId(), medication.getName(),
                medication.getDosage(), dose.getScheduledDate(), dose.getScheduledTime(),
                dose.getStatus(), dose.getTakenAt()
        );
    }
}
