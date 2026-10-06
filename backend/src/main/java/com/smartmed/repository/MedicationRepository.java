package com.smartmed.repository;

import com.smartmed.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicationRepository extends JpaRepository<Medication, Long> {
    List<Medication> findAllByPatientIdAndActiveTrueOrderByCreatedAtDesc(Long patientId);
    Optional<Medication> findByIdAndPatientIdAndActiveTrue(Long id, Long patientId);
    List<Medication> findAllByIdInAndPatientIdAndActiveTrue(List<Long> ids, Long patientId);
    boolean existsByIdAndPatientIdAndActiveTrue(Long id, Long patientId);
}
