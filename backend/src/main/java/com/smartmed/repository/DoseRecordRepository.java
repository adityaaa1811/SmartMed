package com.smartmed.repository;

import com.smartmed.entity.DoseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DoseRecordRepository extends JpaRepository<DoseRecord, Long> {

    List<DoseRecord> findAllByScheduleIdAndScheduledDate(Long scheduleId, LocalDate scheduledDate);

    List<DoseRecord> findAllByScheduledDateAndSchedule_Medication_Patient_IdOrderByScheduledTimeAsc(
            LocalDate scheduledDate, Long patientId);

    List<DoseRecord> findAllByScheduledDateAndSchedule_Medication_Patient_IdAndSchedule_ActiveTrueOrderByScheduledTimeAsc(
            LocalDate scheduledDate, Long patientId);

    Optional<DoseRecord> findByIdAndSchedule_Medication_Patient_Id(Long id, Long patientId);

    Optional<DoseRecord> findByIdAndSchedule_Medication_Patient_IdAndSchedule_ActiveTrue(Long id, Long patientId);

    List<DoseRecord> findAllBySchedule_Medication_Patient_IdAndScheduledDateBetweenOrderByScheduledDateDescScheduledTimeDesc(
            Long patientId, LocalDate from, LocalDate to);

    List<DoseRecord> findAllBySchedule_Medication_Patient_IdAndScheduledDateGreaterThanEqualOrderByScheduledDateDescScheduledTimeDesc(
            Long patientId, LocalDate from);

    List<DoseRecord> findAllBySchedule_Medication_Patient_IdAndScheduledDateLessThanEqualOrderByScheduledDateDescScheduledTimeDesc(
            Long patientId, LocalDate to);

    List<DoseRecord> findAllBySchedule_Medication_Patient_IdOrderByScheduledDateDescScheduledTimeDesc(Long patientId);
}
