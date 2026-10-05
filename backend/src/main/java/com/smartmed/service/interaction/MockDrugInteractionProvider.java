package com.smartmed.service.interaction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Development-only provider. Returns no fabricated interactions; UI must show mock/disclaimer state.
 */
@Component
@ConditionalOnProperty(name = "smartmed.interaction.provider", havingValue = "mock", matchIfMissing = true)
public class MockDrugInteractionProvider implements DrugInteractionProvider {

    @Override
    public String getProviderId() {
        return "mock";
    }

    @Override
    public List<DrugInteractionResult> checkInteractions(List<String> medicationNames) {
        if (medicationNames == null || medicationNames.size() < 2) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
