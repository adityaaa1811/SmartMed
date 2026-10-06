package com.smartmed.repository;

import com.smartmed.entity.CareRelationship;
import com.smartmed.entity.RelationshipStatus;
import com.smartmed.entity.RelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CareRelationshipRepository extends JpaRepository<CareRelationship, Long> {

    boolean existsByPatientIdAndRelatedUserIdAndRelationshipTypeAndStatusIn(
            Long patientId, Long relatedUserId, RelationshipType relationshipType,
            Collection<RelationshipStatus> statuses);

    @EntityGraph(attributePaths = {"patient", "relatedUser"})
    List<CareRelationship> findAllByPatientIdOrderByCreatedAtDesc(Long patientId);

    @EntityGraph(attributePaths = {"patient", "relatedUser"})
    List<CareRelationship> findAllByPatientIdAndRelationshipTypeInAndStatus(
            Long patientId, Collection<RelationshipType> relationshipTypes, RelationshipStatus status);

    @EntityGraph(attributePaths = {"patient", "relatedUser"})
    List<CareRelationship> findAllByRelatedUserIdOrderByCreatedAtDesc(Long relatedUserId);

    @EntityGraph(attributePaths = {"patient", "relatedUser"})
    List<CareRelationship> findAllByRelatedUserIdAndRelationshipTypeAndStatusOrderByCreatedAtDesc(
            Long relatedUserId, RelationshipType relationshipType, RelationshipStatus status);

    Optional<CareRelationship> findByIdAndPatientId(Long id, Long patientId);

    Optional<CareRelationship> findByIdAndRelatedUserId(Long id, Long relatedUserId);

    Optional<CareRelationship> findByPatientIdAndRelatedUserIdAndRelationshipTypeAndStatus(
            Long patientId, Long relatedUserId, RelationshipType relationshipType, RelationshipStatus status);
}
