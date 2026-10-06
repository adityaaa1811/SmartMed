package com.smartmed.service;

import com.smartmed.dto.response.DoseResponse;
import com.smartmed.entity.DoseRecord;
import com.smartmed.entity.DoseStatus;
import com.smartmed.entity.MedicationSchedule;
import com.smartmed.entity.Role;
import com.smartmed.exception.InvalidDateRangeException;
import com.smartmed.exception.InvalidAdherenceHistoryPageException;
import com.smartmed.exception.InvalidDoseTransitionException;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.DoseRecordRepository;
import com.smartmed.repository.MedicationScheduleRepository;
import com.smartmed.repository.UserRepository;
import com.smartmed.security.SmartMedUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdherenceService {

    private static final int MINUTES_PER_DAY = 24 * 60;
    public static final int DEFAULT_HISTORY_PAGE_SIZE = 50;
    public static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final MedicationScheduleRepository scheduleRepository;
    private final DoseRecordRepository doseRepository;
    private final NotificationEventService notificationEventService;
    private final AdherenceAttentionEvaluator adherenceAttentionEvaluator;
    private final UserRepository userRepository;
    private final Clock clock;

    public AdherenceService(MedicationScheduleRepository scheduleRepository,
                            DoseRecordRepository doseRepository,
                            NotificationEventService notificationEventService,
                            AdherenceAttentionEvaluator adherenceAttentionEvaluator,
                            UserRepository userRepository, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.doseRepository = doseRepository;
        this.notificationEventService = notificationEventService;
        this.adherenceAttentionEvaluator = adherenceAttentionEvaluator;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public List<DoseResponse> today(SmartMedUserDetails principal) {
        requirePatient(principal);
        userRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate today = LocalDate.now(clock);
        List<MedicationSchedule> schedules = scheduleRepository.findActiveSchedulesForPatient(principal.getId())
                .stream().filter(schedule -> appliesOn(schedule, today)).toList();
        if (!schedules.isEmpty()) {
            List<Long> scheduleIds = schedules.stream().map(MedicationSchedule::getId).toList();
            Map<Long, List<DoseRecord>> existingBySchedule = doseRepository
                    .findAllByScheduleIdInAndScheduledDate(scheduleIds, today).stream()
                    .collect(Collectors.groupingBy(record -> record.getSchedule().getId()));
            for (MedicationSchedule schedule : schedules) {
                materializeForDate(schedule, today, existingBySchedule.getOrDefault(schedule.getId(), List.of()));
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
                        LocalDate.now(clock), patientId)
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
    public List<DoseResponse> history(LocalDate from, LocalDate to, int page, int pageSize,
                                      SmartMedUserDetails principal) {
        requirePatient(principal);
        if (page < 0 || pageSize < 1 || pageSize > MAX_HISTORY_PAGE_SIZE) {
            throw new InvalidAdherenceHistoryPageException(
                    "page must be non-negative and pageSize must be between 1 and " + MAX_HISTORY_PAGE_SIZE);
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveTo = to == null ? today : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(89) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new InvalidDateRangeException("from date cannot be after to date");
        }
        if (ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) > 365) {
            throw new InvalidDateRangeException("History date range cannot exceed 366 days");
        }
        return doseRepository.findAllBySchedule_Medication_Patient_IdAndScheduledDateBetween(
                        principal.getId(), effectiveFrom, effectiveTo,
                        PageRequest.of(page, pageSize,
                                Sort.by(Sort.Order.desc("scheduledDate"), Sort.Order.desc("scheduledTime"))))
                .map(DoseResponse::from).toList();
    }

    private DoseResponse transition(Long doseId, DoseStatus targetStatus, SmartMedUserDetails principal) {
        requirePatient(principal);
        DoseRecord current = doseRepository.findOwnedDoseForUpdate(doseId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Dose record not found"));
        if (current.getStatus() != DoseStatus.PENDING) {
            throw new InvalidDoseTransitionException(
                    "Dose record is already " + current.getStatus() + " and cannot be changed");
        }
        Instant now = clock.instant();
        int updated = doseRepository.transitionIfPending(doseId, principal.getId(), DoseStatus.PENDING,
                targetStatus, targetStatus == DoseStatus.TAKEN ? now : null, now);
        if (updated == 0) {
            throw new InvalidDoseTransitionException("Dose record was changed by another request");
        }
        DoseRecord savedDose = doseRepository.findByIdAndSchedule_Medication_Patient_IdAndSchedule_ActiveTrue(
                        doseId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Dose record not found"));
        if (targetStatus == DoseStatus.MISSED) notificationEventService.missedDose(savedDose);
        adherenceAttentionEvaluator.evaluateMonthToDate(savedDose.getSchedule().getMedication().getPatient().getId());
        return DoseResponse.from(savedDose);
    }

    private void materializeForDate(MedicationSchedule schedule, LocalDate date, List<DoseRecord> existing) {
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
