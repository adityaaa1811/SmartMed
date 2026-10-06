package com.smartmed.repository;

import com.smartmed.entity.MedicationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {

    List<MedicationSchedule> findAllByMedicationIdAndMedicationPatientIdOrderByTimeOfDayAsc(
            Long medicationId, Long patientId);

    Optional<MedicationSchedule> findByIdAndMedicationPatientId(Long id, Long patientId);

    @Query("select s from MedicationSchedule s join fetch s.medication m "
            + "where m.patient.id = :patientId and m.active = true and s.active = true")
    List<MedicationSchedule> findActiveSchedulesForPatient(@Param("patientId") Long patientId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MedicationSchedule s set s.active = false, s.updatedAt = :updatedAt "
            + "where s.medication.id = :medicationId and s.medication.patient.id = :patientId and s.active = true")
    int deactivateAllForMedication(@Param("medicationId") Long medicationId, @Param("patientId") Long patientId,
                                   @Param("updatedAt") java.time.Instant updatedAt);
}
