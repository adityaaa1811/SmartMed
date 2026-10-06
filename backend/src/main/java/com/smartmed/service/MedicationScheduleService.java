package com.smartmed.service;

import com.smartmed.dto.request.CreateScheduleRequest;
import com.smartmed.dto.request.UpdateScheduleRequest;
import com.smartmed.dto.response.ScheduleResponse;
import com.smartmed.entity.MedicationSchedule;
import com.smartmed.entity.Role;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.MedicationScheduleRepository;
import com.smartmed.security.SmartMedUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MedicationScheduleService {

    private final MedicationRepository medicationRepository;
    private final MedicationScheduleRepository scheduleRepository;

    public MedicationScheduleService(MedicationRepository medicationRepository,
                                     MedicationScheduleRepository scheduleRepository) {
        this.medicationRepository = medicationRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional
    public ScheduleResponse create(Long medicationId, CreateScheduleRequest request,
                                   SmartMedUserDetails principal) {
        requirePatient(principal);
        var medication = medicationRepository.findByIdAndPatientId(medicationId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medication not found"));
        MedicationSchedule schedule = new MedicationSchedule();
        schedule.setMedication(medication);
        apply(schedule, request.frequency(), request.timeOfDay(), request.startDate(), request.endDate(), true);
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(Long medicationId, SmartMedUserDetails principal) {
        requirePatient(principal);
        if (!medicationRepository.existsByIdAndPatientId(medicationId, principal.getId())) {
            throw new ResourceNotFoundException("Medication not found");
        }
        return scheduleRepository.findAllByMedicationIdAndMedicationPatientIdOrderByTimeOfDayAsc(
                        medicationId, principal.getId()).stream()
                .filter(schedule -> Boolean.TRUE.equals(schedule.getActive()))
                .map(ScheduleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(Long scheduleId, SmartMedUserDetails principal) {
        requirePatient(principal);
        return ownedActiveSchedule(scheduleId, principal.getId()).map(ScheduleResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
    }

    @Transactional
    public ScheduleResponse update(Long scheduleId, UpdateScheduleRequest request,
                                   SmartMedUserDetails principal) {
        requirePatient(principal);
        MedicationSchedule schedule = ownedActiveSchedule(scheduleId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        apply(schedule, request.frequency(), request.timeOfDay(), request.startDate(),
                request.endDate(), request.active());
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public void delete(Long scheduleId, SmartMedUserDetails principal) {
        requirePatient(principal);
        MedicationSchedule schedule = ownedActiveSchedule(scheduleId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        schedule.setActive(false);
        scheduleRepository.save(schedule);
    }

    private java.util.Optional<MedicationSchedule> ownedActiveSchedule(Long id, Long patientId) {
        return scheduleRepository.findByIdAndMedicationPatientId(id, patientId)
                .filter(schedule -> Boolean.TRUE.equals(schedule.getActive()));
    }

    private static void requirePatient(SmartMedUserDetails principal) {
        if (principal.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can manage medication schedules");
        }
    }

    private static void apply(MedicationSchedule schedule, com.smartmed.entity.ScheduleFrequency frequency,
                              java.time.LocalTime timeOfDay, java.time.LocalDate startDate,
                              java.time.LocalDate endDate, boolean active) {
        schedule.setFrequency(frequency);
        schedule.setTimeOfDay(timeOfDay);
        schedule.setStartDate(startDate);
        schedule.setEndDate(endDate);
        schedule.setActive(active);
    }
}
