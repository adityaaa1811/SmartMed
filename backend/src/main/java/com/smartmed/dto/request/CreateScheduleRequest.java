package com.smartmed.dto.request;

import com.smartmed.entity.ScheduleFrequency;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateScheduleRequest(
        @NotNull ScheduleFrequency frequency,
        @NotNull LocalTime timeOfDay,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Positive(message = "Dose size must be greater than zero") Long doseSize
) {
    @AssertTrue(message = "endDate cannot be before startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
