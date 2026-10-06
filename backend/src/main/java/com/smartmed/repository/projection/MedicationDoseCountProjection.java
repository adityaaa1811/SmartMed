package com.smartmed.repository.projection;

import com.smartmed.entity.DoseStatus;

public interface MedicationDoseCountProjection {
    Long getMedicationId();
    String getMedicationName();
    DoseStatus getStatus();
    Long getDoseCount();
}
