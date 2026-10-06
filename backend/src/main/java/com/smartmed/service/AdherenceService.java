package com.smartmed.service;

import com.smartmed.dto.response.DoseResponse;
import com.smartmed.entity.DoseRecord;
import com.smartmed.entity.DoseStatus;
import com.smartmed.entity.MedicationSchedule;
import com.smartmed.entity.Role;
import com.smartmed.exception.InvalidDateRangeException;
import com.smartmed.exception.InvalidDoseTransitionException;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.DoseRecordRepository;
import com.smartmed.repository.MedicationScheduleRepository;
import com.smartmed.security.SmartMedUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdherenceService {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private final MedicationScheduleRepository scheduleRepository;
    private final DoseRecordRepository doseRepository;
    private final NotificationEventService notificationEventService;
    private final AdherenceAttentionEvaluator adherenceAttentionEvaluator;

    public AdherenceService(MedicationScheduleRepository scheduleRepository,
                            DoseRecordRepository doseRepository,
                            NotificationEventService notificationEventService,
                            AdherenceAttentionEvaluator adherenceAttentionEvaluator) {
        this.scheduleRepository = scheduleRepository;
        this.doseRepository = doseRepository;
        this.notificationEventService = notificationEventService;
        this.adherenceAttentionEvaluator = adherenceAttentionEvaluator;
    }

    @Transactional
    public List<DoseResponse> today(SmartMedUserDetails principal) {
        requirePatient(principal);
        LocalDate today = LocalDate.now();
        for (MedicationSchedule schedule : scheduleRepository.findActiveSchedulesForPatient(principal.getId())) {
            if (appliesOn(schedule, today)) {
                materializeForDate(schedule, today);
            }
        }
        return doseRepository.findAllByScheduledDateAndSchedule_Medication_Patient_IdAndSchedule_ActiveTrueOrderByScheduledTimeAsc(
                        today, principal.getId()).stream()
                .map(DoseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    List<DoseResponse> todayForAuthorizedPatient(Long patientId) {
        return doseRepository
                .findAllByScheduledDateAndSchedule_Medication_Patient_IdAndSchedule_ActiveTrueOrderByScheduledTimeAsc(
                        LocalDate.now(), patientId)
                .stream()
                .map(DoseResponse::from)
                .toList();
    }

    @Transactional
    public DoseResponse markTaken(Long doseId, SmartMedUserDetails principal) {
        return transition(doseId, DoseStatus.TAKEN, principal);
    }

    @Transactional
    public DoseResponse markMissed(Long doseId, SmartMedUserDetails principal) {
        return transition(doseId, DoseStatus.MISSED, principal);
    }

    @Transactional
    public DoseResponse markSkipped(Long doseId, SmartMedUserDetails principal) {
        return transition(doseId, DoseStatus.SKIPPED, principal);
    }

    @Transactional(readOnly = true)
    public List<DoseResponse> history(LocalDate from, LocalDate to, SmartMedUserDetails principal) {
        requirePatient(principal);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("from date cannot be after to date");
        }

        List<DoseRecord> records;
        if (from != null && to != null) {
            records = doseRepository
                    .findAllBySchedule_Medication_Patient_IdAndScheduledDateBetweenOrderByScheduledDateDescScheduledTimeDesc(
                            principal.getId(), from, to);
        } else if (from != null) {
            records = doseRepository
                    .findAllBySchedule_Medication_Patient_IdAndScheduledDateGreaterThanEqualOrderByScheduledDateDescScheduledTimeDesc(
                            principal.getId(), from);
        } else if (to != null) {
            records = doseRepository
                    .findAllBySchedule_Medication_Patient_IdAndScheduledDateLessThanEqualOrderByScheduledDateDescScheduledTimeDesc(
                            principal.getId(), to);
        } else {
            records = doseRepository
                    .findAllBySchedule_Medication_Patient_IdOrderByScheduledDateDescScheduledTimeDesc(
                            principal.getId());
        }
        return records.stream().map(DoseResponse::from).toList();
    }

    private DoseResponse transition(Long doseId, DoseStatus targetStatus, SmartMedUserDetails principal) {
        requirePatient(principal);
        DoseRecord dose = doseRepository.findByIdAndSchedule_Medication_Patient_IdAndSchedule_ActiveTrue(
                        doseId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Dose record not found"));
        if (dose.getStatus() != DoseStatus.PENDING) {
            throw new InvalidDoseTransitionException(
                    "Dose record is already " + dose.getStatus() + " and cannot be changed");
        }
        dose.setStatus(targetStatus);
        dose.setTakenAt(targetStatus == DoseStatus.TAKEN ? java.time.Instant.now() : null);
        DoseRecord savedDose = doseRepository.save(dose);
        if (targetStatus == DoseStatus.MISSED) {
            notificationEventService.missedDose(savedDose);
        }
        adherenceAttentionEvaluator.evaluateMonthToDate(
                savedDose.getSchedule().getMedication().getPatient().getId());
        return DoseResponse.from(savedDose);
    }

    private void materializeForDate(MedicationSchedule schedule, LocalDate date) {
        List<DoseRecord> existing = doseRepository.findAllByScheduleIdAndScheduledDate(schedule.getId(), date);
        Map<LocalTime, DoseRecord> byTime = new HashMap<>();
        existing.forEach(record -> byTime.put(record.getScheduledTime(), record));

        List<DoseRecord> newRecords = new ArrayList<>();
        int slots = schedule.getFrequency().slotsPerDay();
        int intervalMinutes = MINUTES_PER_DAY / slots;
        int anchorMinutes = schedule.getTimeOfDay().getHour() * 60 + schedule.getTimeOfDay().getMinute();
        for (int slot = 0; slot < slots; slot++) {
            int minuteOfDay = (anchorMinutes + slot * intervalMinutes) % MINUTES_PER_DAY;
            LocalTime scheduledTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
            if (!byTime.containsKey(scheduledTime)) {
                DoseRecord dose = new DoseRecord();
                dose.setSchedule(schedule);
                dose.setScheduledDate(date);
                dose.setScheduledTime(scheduledTime);
                dose.setStatus(DoseStatus.PENDING);
                newRecords.add(dose);
            }
        }
        if (!newRecords.isEmpty()) {
            doseRepository.saveAll(newRecords);
        }
    }

    private static boolean appliesOn(MedicationSchedule schedule, LocalDate date) {
        var medication = schedule.getMedication();
        boolean withinSchedule = !date.isBefore(schedule.getStartDate())
                && (schedule.getEndDate() == null || !date.isAfter(schedule.getEndDate()));
        boolean withinMedication = !date.isBefore(medication.getStartDate())
                && (medication.getEndDate() == null || !date.isAfter(medication.getEndDate()));
        return withinSchedule && withinMedication;
    }

    private static void requirePatient(SmartMedUserDetails principal) {
        if (principal.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can access adherence records");
        }
    }
}
