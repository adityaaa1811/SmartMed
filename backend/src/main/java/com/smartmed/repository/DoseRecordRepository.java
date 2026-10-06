package com.smartmed.repository;

import com.smartmed.entity.DoseRecord;
import com.smartmed.repository.projection.DailyDoseCountProjection;
import com.smartmed.repository.projection.DoseStatusCountProjection;
import com.smartmed.repository.projection.MedicationDoseCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DoseRecordRepository extends JpaRepository<DoseRecord, Long> {

    @Query("select d.status as status, count(d.id) as doseCount from DoseRecord d "
            + "where d.schedule.medication.patient.id = :patientId "
            + "and d.scheduledDate between :from and :to group by d.status")
    List<DoseStatusCountProjection> aggregateStatuses(@Param("patientId") Long patientId,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to);

    @Query("select d.scheduledDate as scheduledDate, d.status as status, count(d.id) as doseCount "
            + "from DoseRecord d where d.schedule.medication.patient.id = :patientId "
            + "and d.scheduledDate between :from and :to "
            + "group by d.scheduledDate, d.status order by d.scheduledDate asc")
    List<DailyDoseCountProjection> aggregateDailyStatuses(@Param("patientId") Long patientId,
                                                           @Param("from") LocalDate from,
                                                           @Param("to") LocalDate to);

    @Query("select d.schedule.medication.id as medicationId, "
            + "d.schedule.medication.name as medicationName, d.status as status, count(d.id) as doseCount "
            + "from DoseRecord d where d.schedule.medication.patient.id = :patientId "
            + "and d.scheduledDate between :from and :to "
            + "group by d.schedule.medication.id, d.schedule.medication.name, d.status "
            + "order by d.schedule.medication.name asc")
    List<MedicationDoseCountProjection> aggregateMedicationStatuses(@Param("patientId") Long patientId,
                                                                     @Param("from") LocalDate from,
                                                                     @Param("to") LocalDate to);

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
