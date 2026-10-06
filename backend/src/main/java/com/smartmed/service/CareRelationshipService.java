package com.smartmed.service;

import com.smartmed.dto.request.CreateCareRelationshipRequest;
import com.smartmed.dto.response.CareRelationshipResponse;
import com.smartmed.entity.CareRelationship;
import com.smartmed.entity.RelationshipStatus;
import com.smartmed.entity.RelationshipType;
import com.smartmed.entity.Role;
import com.smartmed.entity.User;
import com.smartmed.exception.DuplicateCareRelationshipException;
import com.smartmed.exception.InvalidCareRelationshipRequestException;
import com.smartmed.exception.InvalidCareRelationshipStateException;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.CareRelationshipRepository;
import com.smartmed.repository.UserRepository;
import com.smartmed.security.SmartMedUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CareRelationshipService {

    private static final List<RelationshipStatus> OPEN_STATUSES =
            List.of(RelationshipStatus.PENDING, RelationshipStatus.ACTIVE);

    private final CareRelationshipRepository relationshipRepository;
    private final UserRepository userRepository;

    public CareRelationshipService(CareRelationshipRepository relationshipRepository,
                                   UserRepository userRepository) {
        this.relationshipRepository = relationshipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CareRelationshipResponse create(CreateCareRelationshipRequest request, SmartMedUserDetails principal) {
        requireRole(principal, Role.PATIENT);

        // Serializes relationship creation for this patient so concurrent requests cannot create duplicates.
        User patient = userRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        if (patient.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can grant care team access");
        }
        String email = request.relatedUserEmail().trim().toLowerCase(Locale.ROOT);
        User relatedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Related user not found"));

        if (patient.getId().equals(relatedUser.getId())) {
            throw new InvalidCareRelationshipRequestException("A patient cannot create a relationship with themselves");
        }
        Role requiredRole = request.relationshipType() == RelationshipType.CAREGIVER ? Role.CAREGIVER : Role.DOCTOR;
        if (relatedUser.getRole() != requiredRole) {
            throw new InvalidCareRelationshipRequestException("The selected relationship type does not match the user's role");
        }
        if (relationshipRepository.existsByPatientIdAndRelatedUserIdAndRelationshipTypeAndStatusIn(
                patient.getId(), relatedUser.getId(), request.relationshipType(), OPEN_STATUSES)) {
            throw new DuplicateCareRelationshipException("A pending or active relationship already exists");
        }

        CareRelationship relationship = new CareRelationship();
        relationship.setPatient(patient);
        relationship.setRelatedUser(relatedUser);
        relationship.setRelationshipType(request.relationshipType());
        relationship.setStatus(RelationshipStatus.PENDING);
        return CareRelationshipResponse.from(relationshipRepository.save(relationship));
    }

    @Transactional(readOnly = true)
    public List<CareRelationshipResponse> list(SmartMedUserDetails principal) {
        List<CareRelationship> relationships = switch (principal.getRole()) {
            case PATIENT -> relationshipRepository.findAllByPatientIdOrderByCreatedAtDesc(principal.getId());
            case CAREGIVER, DOCTOR -> relationshipRepository.findAllByRelatedUserIdOrderByCreatedAtDesc(principal.getId());
        };
        return relationships.stream().map(CareRelationshipResponse::from).toList();
    }

    @Transactional
    public CareRelationshipResponse accept(Long relationshipId, SmartMedUserDetails principal) {
        CareRelationship relationship = pendingForRelatedUser(relationshipId, principal);
        relationship.setStatus(RelationshipStatus.ACTIVE);
        return CareRelationshipResponse.from(relationshipRepository.save(relationship));
    }

    @Transactional
    public CareRelationshipResponse reject(Long relationshipId, SmartMedUserDetails principal) {
        CareRelationship relationship = pendingForRelatedUser(relationshipId, principal);
        // REJECTED is intentionally represented as REVOKED in the Phase 6 status model.
        relationship.setStatus(RelationshipStatus.REVOKED);
        return CareRelationshipResponse.from(relationshipRepository.save(relationship));
    }

    @Transactional
    public void revoke(Long relationshipId, SmartMedUserDetails principal) {
        requireRole(principal, Role.PATIENT);
        CareRelationship relationship = relationshipRepository.findByIdAndPatientId(relationshipId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Relationship not found"));
        if (relationship.getStatus() != RelationshipStatus.ACTIVE) {
            throw new InvalidCareRelationshipStateException("Only active relationships can be revoked by the patient");
        }
        relationship.setStatus(RelationshipStatus.REVOKED);
        relationshipRepository.save(relationship);
    }

    private CareRelationship pendingForRelatedUser(Long relationshipId, SmartMedUserDetails principal) {
        if (principal.getRole() == Role.PATIENT) {
            throw new AccessDeniedException("Only caregivers and doctors can respond to relationship requests");
        }
        RelationshipType expectedType = principal.getRole() == Role.CAREGIVER
                ? RelationshipType.CAREGIVER : RelationshipType.DOCTOR;
        CareRelationship relationship = relationshipRepository.findByIdAndRelatedUserId(relationshipId, principal.getId())
                .filter(item -> item.getRelationshipType() == expectedType)
                .orElseThrow(() -> new ResourceNotFoundException("Relationship request not found"));
        if (relationship.getStatus() != RelationshipStatus.PENDING) {
            throw new InvalidCareRelationshipStateException("Only pending relationships can be accepted or rejected");
        }
        return relationship;
    }

    private static void requireRole(SmartMedUserDetails principal, Role role) {
        if (principal.getRole() != role) {
            throw new AccessDeniedException("This action is not available to this account role");
        }
    }
}
