package com.smartmed.dto.response;

import com.smartmed.entity.MedicationSchedule;
import com.smartmed.entity.ScheduleFrequency;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id,
        Long medicationId,
        ScheduleFrequency frequency,
        LocalTime timeOfDay,
        LocalDate startDate,
        LocalDate endDate,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static ScheduleResponse from(MedicationSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(), schedule.getMedication().getId(), schedule.getFrequency(),
                schedule.getTimeOfDay(), schedule.getStartDate(), schedule.getEndDate(),
                schedule.getActive(), schedule.getCreatedAt(), schedule.getUpdatedAt()
        );
    }
}
