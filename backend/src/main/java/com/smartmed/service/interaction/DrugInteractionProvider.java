package com.smartmed.service.interaction;

import java.util.List;

/**
 * Abstraction for external drug interaction data (Phase 6).
 * Implementations must not fabricate clinical data when labeled as a real provider.
 */
public interface DrugInteractionProvider {

    String getProviderId();

    List<DrugInteractionResult> checkInteractions(List<String> medicationNames);
}
