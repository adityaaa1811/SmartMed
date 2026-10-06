package com.smartmed.dto.response;

import com.smartmed.entity.CareRelationship;
import com.smartmed.entity.RelationshipStatus;
import com.smartmed.entity.RelationshipType;

import java.time.Instant;

public record CareRelationshipResponse(
        Long id,
        String patientName,
        String patientEmail,
        String relatedUserName,
        String relatedUserEmail,
        RelationshipType relationshipType,
        RelationshipStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static CareRelationshipResponse from(CareRelationship relationship) {
        return new CareRelationshipResponse(
                relationship.getId(),
                relationship.getPatient().getFullName(),
                relationship.getPatient().getEmail(),
                relationship.getRelatedUser().getFullName(),
                relationship.getRelatedUser().getEmail(),
                relationship.getRelationshipType(),
                relationship.getStatus(),
                relationship.getCreatedAt(),
                relationship.getUpdatedAt()
        );
    }
}
