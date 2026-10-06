package com.smartmed.repository.projection;

import com.smartmed.entity.DoseStatus;

import java.time.LocalDate;

public interface DailyDoseCountProjection {
    LocalDate getScheduledDate();
    DoseStatus getStatus();
    Long getDoseCount();
}
