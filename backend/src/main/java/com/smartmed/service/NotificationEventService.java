package com.smartmed.service;

import com.smartmed.entity.CareRelationship;
import com.smartmed.entity.DoseRecord;
import com.smartmed.entity.NotificationRelatedEntityType;
import com.smartmed.entity.NotificationType;
import com.smartmed.entity.RelationshipStatus;
import com.smartmed.entity.RelationshipType;
import com.smartmed.entity.User;
import com.smartmed.repository.CareRelationshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationEventService {

    private static final List<RelationshipType> CARE_TEAM_TYPES = List.of(
            RelationshipType.CAREGIVER, RelationshipType.DOCTOR);

    private final NotificationService notificationService;
    private final CareRelationshipRepository relationshipRepository;

    public NotificationEventService(NotificationService notificationService,
                                    CareRelationshipRepository relationshipRepository) {
        this.notificationService = notificationService;
        this.relationshipRepository = relationshipRepository;
    }

    @Transactional
    public void missedDose(DoseRecord dose) {
        User patient = dose.getSchedule().getMedication().getPatient();
        String medicationName = dose.getSchedule().getMedication().getName();
        String eventKey = "dose:" + dose.getId() + ":missed";

        notificationService.createIfAbsent(patient, NotificationType.MISSED_DOSE,
                "Missed dose recorded", "Missed dose recorded for " + medicationName + ".",
                NotificationRelatedEntityType.DOSE, dose.getId(), eventKey);

        List<CareRelationship> activeRelationships = relationshipRepository
                .findAllByPatientIdAndRelationshipTypeInAndStatus(
                        patient.getId(), CARE_TEAM_TYPES, RelationshipStatus.ACTIVE);
        for (CareRelationship relationship : activeRelationships) {
            notificationService.createIfAbsent(relationship.getRelatedUser(), NotificationType.MISSED_DOSE,
                    "Missed dose recorded", "A connected patient recorded a missed dose for "
                            + medicationName + ".", NotificationRelatedEntityType.DOSE, dose.getId(), eventKey);
        }
    }

    @Transactional
    public void relationshipRequested(CareRelationship relationship) {
        User patient = relationship.getPatient();
        User relatedUser = relationship.getRelatedUser();
        notificationService.createIfAbsent(relatedUser, NotificationType.CARE_RELATIONSHIP,
                "Care team request", patient.getFullName() + " sent you a "
                        + relationshipLabel(relationship) + " connection request.",
                NotificationRelatedEntityType.CARE_RELATIONSHIP, relationship.getId(),
                relationshipKey(relationship, "requested"));
    }

    @Transactional
    public void relationshipActivated(CareRelationship relationship) {
        User patient = relationship.getPatient();
        User relatedUser = relationship.getRelatedUser();
        String label = relationshipLabel(relationship);
        notificationService.createIfAbsent(patient, NotificationType.CARE_RELATIONSHIP,
                "Care team connection active", relatedUser.getFullName() + " accepted your "
                        + label + " connection request.",
                NotificationRelatedEntityType.CARE_RELATIONSHIP, relationship.getId(),
                relationshipKey(relationship, "activated"));
        notificationService.createIfAbsent(relatedUser, NotificationType.CARE_RELATIONSHIP,
                "Care team connection active", "Your " + label + " connection with "
                        + patient.getFullName() + " is now active.",
                NotificationRelatedEntityType.CARE_RELATIONSHIP, relationship.getId(),
                relationshipKey(relationship, "activated"));
    }

    @Transactional
    public void relationshipDeclined(CareRelationship relationship) {
        User patient = relationship.getPatient();
        User relatedUser = relationship.getRelatedUser();
        notificationService.createIfAbsent(patient, NotificationType.CARE_RELATIONSHIP,
                "Care team request declined", relatedUser.getFullName() + " declined your "
                        + relationshipLabel(relationship) + " connection request.",
                NotificationRelatedEntityType.CARE_RELATIONSHIP, relationship.getId(),
                relationshipKey(relationship, "declined"));
    }

    @Transactional
    public void relationshipRevoked(CareRelationship relationship) {
        User patient = relationship.getPatient();
        User relatedUser = relationship.getRelatedUser();
        notificationService.createIfAbsent(relatedUser, NotificationType.CARE_RELATIONSHIP,
                "Care team access revoked", patient.getFullName() + " revoked your "
                        + relationshipLabel(relationship) + " access.",
                NotificationRelatedEntityType.CARE_RELATIONSHIP, relationship.getId(),
                relationshipKey(relationship, "revoked"));
    }

    private static String relationshipLabel(CareRelationship relationship) {
        return relationship.getRelationshipType() == RelationshipType.CAREGIVER ? "caregiver" : "doctor";
    }

    private static String relationshipKey(CareRelationship relationship, String transition) {
        return "relationship:" + relationship.getId() + ":" + transition;
    }
}
