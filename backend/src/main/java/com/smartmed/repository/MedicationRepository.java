package com.smartmed.repository;

import com.smartmed.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository extends JpaRepository<Medication, Long> {
    List<Medication> findAllByPatientIdAndActiveTrueOrderByCreatedAtDesc(Long patientId);
    Optional<Medication> findByIdAndPatientIdAndActiveTrue(Long id, Long patientId);
    List<Medication> findAllByIdInAndPatientIdAndActiveTrue(List<Long> ids, Long patientId);
    boolean existsByIdAndPatientIdAndActiveTrue(Long id, Long patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Medication m where m.id = :id and m.patient.id = :patientId and m.active = true")
    Optional<Medication> findByIdAndPatientIdAndActiveTrueForUpdate(@Param("id") Long id, @Param("patientId") Long patientId);
}
