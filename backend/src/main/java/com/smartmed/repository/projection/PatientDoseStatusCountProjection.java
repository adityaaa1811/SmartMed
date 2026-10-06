package com.smartmed.repository.projection;

import com.smartmed.entity.DoseStatus;

public interface PatientDoseStatusCountProjection {
    Long getPatientId();
    DoseStatus getStatus();
    Long getDoseCount();
}
