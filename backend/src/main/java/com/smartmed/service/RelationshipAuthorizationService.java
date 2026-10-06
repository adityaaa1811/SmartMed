package com.smartmed.service;

import com.smartmed.entity.CareRelationship;
import com.smartmed.entity.RelationshipStatus;
import com.smartmed.entity.RelationshipType;
import com.smartmed.entity.Role;
import com.smartmed.entity.User;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.CareRelationshipRepository;
import com.smartmed.security.SmartMedUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RelationshipAuthorizationService {

    private final CareRelationshipRepository relationshipRepository;

    public RelationshipAuthorizationService(CareRelationshipRepository relationshipRepository) {
        this.relationshipRepository = relationshipRepository;
    }

    @Transactional(readOnly = true)
    public User requireActivePatient(SmartMedUserDetails viewer, Long patientId, RelationshipType expectedType) {
        requireRelatedRole(viewer, expectedType);
        return relationshipRepository.findByPatientIdAndRelatedUserIdAndRelationshipTypeAndStatus(
                        patientId, viewer.getId(), expectedType, RelationshipStatus.ACTIVE)
                .map(CareRelationship::getPatient)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }

    @Transactional(readOnly = true)
    public List<User> activePatients(SmartMedUserDetails viewer, RelationshipType expectedType) {
        requireRelatedRole(viewer, expectedType);
        return relationshipRepository.findAllByRelatedUserIdAndRelationshipTypeAndStatusOrderByCreatedAtDesc(
                        viewer.getId(), expectedType, RelationshipStatus.ACTIVE)
                .stream()
                .map(CareRelationship::getPatient)
                .toList();
    }

    void requireRelatedRole(SmartMedUserDetails viewer, RelationshipType expectedType) {
        Role expectedRole = expectedType == RelationshipType.CAREGIVER ? Role.CAREGIVER : Role.DOCTOR;
        if (viewer.getRole() != expectedRole) {
            throw new AccessDeniedException("This monitoring area is not available to this account role");
        }
    }
}
