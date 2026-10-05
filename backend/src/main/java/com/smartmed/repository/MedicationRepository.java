package com.smartmed.repository;

import com.smartmed.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicationRepository extends JpaRepository<Medication, Long> {
    List<Medication> findAllByPatientIdOrderByCreatedAtDesc(Long patientId);
    Optional<Medication> findByIdAndPatientId(Long id, Long patientId);
}
