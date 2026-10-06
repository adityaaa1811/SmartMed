package com.smartmed.service;

import com.smartmed.dto.request.InteractionCheckRequest;
import com.smartmed.dto.response.InteractionCheckResponse;
import com.smartmed.dto.response.InteractionCheckStatus;
import com.smartmed.dto.response.InteractionResponse;
import com.smartmed.entity.Medication;
import com.smartmed.entity.Role;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.interaction.DrugInteractionProvider;
import com.smartmed.service.interaction.DrugInteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InteractionService {

    private static final Logger log = LoggerFactory.getLogger(InteractionService.class);

    private final MedicationRepository medicationRepository;
    private final DrugInteractionProvider drugInteractionProvider;

    public InteractionService(MedicationRepository medicationRepository,
                              DrugInteractionProvider drugInteractionProvider) {
        this.medicationRepository = medicationRepository;
        this.drugInteractionProvider = drugInteractionProvider;
    }

    @Transactional(readOnly = true)
    public InteractionCheckResponse check(InteractionCheckRequest request, SmartMedUserDetails principal) {
        requirePatient(principal);
        List<Long> medicationIds = request.medicationIds();
        List<Medication> ownedMedications = medicationRepository
                .findAllByIdInAndPatientId(medicationIds, principal.getId());
        if (ownedMedications.size() != medicationIds.size()) {
            throw new ResourceNotFoundException("Medication not found");
        }

        Map<Long, Medication> medicationById = ownedMedications.stream()
                .collect(Collectors.toMap(Medication::getId, Function.identity()));
        List<String> medicationNames = medicationIds.stream()
                .map(medicationById::get)
                .map(Medication::getName)
                .toList();

        String providerId = null;
        try {
            providerId = drugInteractionProvider.getProviderId();
            List<DrugInteractionResult> providerResults = drugInteractionProvider.checkInteractions(medicationNames);
            if (providerResults == null) {
                return unavailable(medicationIds.size(), providerId);
            }
            List<InteractionResponse> interactions = providerResults.stream()
                    .map(InteractionResponse::from)
                    .toList();
            InteractionCheckStatus status = interactions.isEmpty()
                    ? InteractionCheckStatus.NO_DATA : InteractionCheckStatus.SUCCESS;
            return new InteractionCheckResponse(status, medicationIds.size(), providerId, interactions);
        } catch (RuntimeException ex) {
            log.warn("Drug interaction provider failed; reporting the check as unavailable");
            return unavailable(medicationIds.size(), providerId);
        }
    }

    private static InteractionCheckResponse unavailable(int count, String providerId) {
        return new InteractionCheckResponse(InteractionCheckStatus.PROVIDER_UNAVAILABLE,
                count, providerId, List.of());
    }

    private static void requirePatient(SmartMedUserDetails principal) {
        if (principal.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can check medication interactions");
        }
    }
}
