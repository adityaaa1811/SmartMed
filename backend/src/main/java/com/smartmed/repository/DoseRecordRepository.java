package com.smartmed.repository;

import com.smartmed.entity.DoseRecord;
import com.smartmed.entity.DoseStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.smartmed.repository.projection.DailyDoseCountProjection;
import com.smartmed.repository.projection.DoseStatusCountProjection;
import com.smartmed.repository.projection.MedicationDoseCountProjection;
import com.smartmed.repository.projection.PatientDoseStatusCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DoseRecordRepository extends JpaRepository<DoseRecord, Long> {

    @Query("select d.status as status, count(d.id) as doseCount from DoseRecord d "
            + "where d.schedule.medication.patient.id = :patientId "
            + "and d.scheduledDate between :from and :to and d.status <> :cancelled group by d.status")
    List<DoseStatusCountProjection> aggregateStatuses(@Param("patientId") Long patientId,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to,
                                                       @Param("cancelled") DoseStatus cancelled);

    @Query("select d.schedule.medication.patient.id as patientId, d.status as status, count(d.id) as doseCount "
            + "from DoseRecord d where d.schedule.medication.patient.id in :patientIds "
            + "and d.scheduledDate between :from and :to and d.status <> :cancelled "
            + "group by d.schedule.medication.patient.id, d.status")
    List<PatientDoseStatusCountProjection> aggregateStatusesForPatients(
            @Param("patientIds") List<Long> patientIds, @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("cancelled") DoseStatus cancelled);

    @Query("select d.scheduledDate as scheduledDate, d.status as status, count(d.id) as doseCount "
            + "from DoseRecord d where d.schedule.medication.patient.id = :patientId "
            + "and d.scheduledDate between :from and :to and d.status <> :cancelled "
            + "group by d.scheduledDate, d.status order by d.scheduledDate asc")
    List<DailyDoseCountProjection> aggregateDailyStatuses(@Param("patientId") Long patientId,
                                                           @Param("from") LocalDate from,
                                                           @Param("to") LocalDate to,
                                                           @Param("cancelled") DoseStatus cancelled);

    @Query("select d.schedule.medication.id as medicationId, "
            + "d.schedule.medication.name as medicationName, d.status as status, count(d.id) as doseCount "
            + "from DoseRecord d where d.schedule.medication.patient.id = :patientId "
            + "and d.scheduledDate between :from and :to and d.status <> :cancelled "
            + "group by d.schedule.medication.id, d.schedule.medication.name, d.status "
            + "order by d.schedule.medication.name asc")
    List<MedicationDoseCountProjection> aggregateMedicationStatuses(@Param("patientId") Long patientId,
                                                                     @Param("from") LocalDate from,
                                                                     @Param("to") LocalDate to,
                                                                     @Param("cancelled") DoseStatus cancelled);

    List<DoseRecord> findAllByScheduleIdInAndScheduledDate(List<Long> scheduleIds, LocalDate scheduledDate);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DoseRecord d set d.status = :cancelled, d.updatedAt = :updatedAt "
            + "where d.status = :pending and d.schedule.id = :scheduleId")
    int cancelPendingForSchedule(@Param("scheduleId") Long scheduleId, @Param("pending") DoseStatus pending,
                                 @Param("cancelled") DoseStatus cancelled,
                                 @Param("updatedAt") java.time.Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DoseRecord d set d.status = :cancelled, d.updatedAt = :updatedAt "
            + "where d.status = :pending and d.schedule.id in "
            + "(select s.id from MedicationSchedule s where s.medication.id = :medicationId)")
    int cancelPendingForMedication(@Param("medicationId") Long medicationId, @Param("pending") DoseStatus pending,
                                   @Param("cancelled") DoseStatus cancelled,
                                   @Param("updatedAt") java.time.Instant updatedAt);

    @EntityGraph(attributePaths = {"schedule", "schedule.medication"})
    Page<DoseRecord> findAllBySchedule_Medication_Patient_IdAndScheduledDateBetween(
            Long patientId, LocalDate from, LocalDate to, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DoseRecord d where d.id = :id and d.schedule.active = true "
            + "and d.schedule.medication.active = true and d.schedule.medication.patient.id = :patientId")
    Optional<DoseRecord> findOwnedDoseForUpdate(@Param("id") Long id, @Param("patientId") Long patientId);

    @Modifying(clearAutomatically = true)
    @Query("update DoseRecord d set d.status = :targetStatus, d.takenAt = :takenAt, d.updatedAt = :updatedAt "
            + "where d.id = :id and d.status = :expectedStatus and d.schedule.active = true "
            + "and d.schedule.medication.active = true and d.schedule.medication.patient.id = :patientId")
    int transitionIfPending(@Param("id") Long id, @Param("patientId") Long patientId,
                            @Param("expectedStatus") DoseStatus expectedStatus,
                            @Param("targetStatus") DoseStatus targetStatus,
                            @Param("takenAt") java.time.Instant takenAt,
                            @Param("updatedAt") java.time.Instant updatedAt);

    List<DoseRecord> findAllByScheduledDateAndSchedule_Medication_Patient_IdOrderByScheduledTimeAsc(
            LocalDate scheduledDate, Long patientId);

    @EntityGraph(attributePaths = {"schedule", "schedule.medication"})
    List<DoseRecord> findAllByScheduledDateAndSchedule_Medication_Patient_IdAndSchedule_ActiveTrueOrderByScheduledTimeAsc(
            LocalDate scheduledDate, Long patientId);

    Optional<DoseRecord> findByIdAndSchedule_Medication_Patient_Id(Long id, Long patientId);

    Optional<DoseRecord> findByIdAndSchedule_Medication_Patient_IdAndSchedule_ActiveTrue(Long id, Long patientId);

    @EntityGraph(attributePaths = {"schedule", "schedule.medication"})
    List<DoseRecord> findAllBySchedule_Medication_Patient_IdAndScheduledDateBetweenOrderByScheduledDateDescScheduledTimeDesc(
            Long patientId, LocalDate from, LocalDate to);

    List<DoseRecord> findAllBySchedule_Medication_Patient_IdAndScheduledDateGreaterThanEqualOrderByScheduledDateDescScheduledTimeDesc(
            Long patientId, LocalDate from);

    List<DoseRecord> findAllBySchedule_Medication_Patient_IdAndScheduledDateLessThanEqualOrderByScheduledDateDescScheduledTimeDesc(
            Long patientId, LocalDate to);

    List<DoseRecord> findAllBySchedule_Medication_Patient_IdOrderByScheduledDateDescScheduledTimeDesc(Long patientId);
}
