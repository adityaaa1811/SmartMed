package com.smartmed.repository;

import com.smartmed.entity.CareRelationship;
import com.smartmed.entity.RelationshipStatus;
import com.smartmed.entity.RelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CareRelationshipRepository extends JpaRepository<CareRelationship, Long> {

    boolean existsByPatientIdAndRelatedUserIdAndRelationshipTypeAndStatusIn(
            Long patientId, Long relatedUserId, RelationshipType relationshipType,
            Collection<RelationshipStatus> statuses);

    List<CareRelationship> findAllByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<CareRelationship> findAllByPatientIdAndRelationshipTypeInAndStatus(
            Long patientId, Collection<RelationshipType> relationshipTypes, RelationshipStatus status);

    List<CareRelationship> findAllByRelatedUserIdOrderByCreatedAtDesc(Long relatedUserId);

    List<CareRelationship> findAllByRelatedUserIdAndRelationshipTypeAndStatusOrderByCreatedAtDesc(
            Long relatedUserId, RelationshipType relationshipType, RelationshipStatus status);

    Optional<CareRelationship> findByIdAndPatientId(Long id, Long patientId);

    Optional<CareRelationship> findByIdAndRelatedUserId(Long id, Long relatedUserId);

    Optional<CareRelationship> findByPatientIdAndRelatedUserIdAndRelationshipTypeAndStatus(
            Long patientId, Long relatedUserId, RelationshipType relationshipType, RelationshipStatus status);
}
