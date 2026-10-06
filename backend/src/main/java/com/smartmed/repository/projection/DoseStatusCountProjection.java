package com.smartmed.repository.projection;

import com.smartmed.entity.DoseStatus;

public interface DoseStatusCountProjection {
    DoseStatus getStatus();
    Long getDoseCount();
}
