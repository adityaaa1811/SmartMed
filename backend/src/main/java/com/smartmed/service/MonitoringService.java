package com.smartmed.service;

import com.smartmed.dto.response.AnalyticsSummaryResponse;
import com.smartmed.dto.response.MedicationResponse;
import com.smartmed.dto.response.MonitoringDoseResponse;
import com.smartmed.dto.response.PatientAnalyticsResponse;
import com.smartmed.dto.response.PatientMonitoringSummaryResponse;
import com.smartmed.dto.response.PatientOverviewResponse;
import com.smartmed.dto.response.ScheduleResponse;
import com.smartmed.entity.Medication;
import com.smartmed.entity.MedicationSchedule;
import com.smartmed.entity.RelationshipType;
import com.smartmed.entity.User;
import com.smartmed.repository.CareRelationshipRepository;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.MedicationScheduleRepository;
import com.smartmed.security.SmartMedUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MonitoringService {

    private final CareRelationshipRepository relationshipRepository;
    private final RelationshipAuthorizationService authorizationService;
    private final AnalyticsService analyticsService;
    private final AdherenceService adherenceService;
    private final MedicationRepository medicationRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final Clock clock;

    public MonitoringService(CareRelationshipRepository relationshipRepository,
                             RelationshipAuthorizationService authorizationService,
                             AnalyticsService analyticsService,
                             AdherenceService adherenceService,
                             MedicationRepository medicationRepository,
                             MedicationScheduleRepository scheduleRepository, Clock clock) {
        this.relationshipRepository = relationshipRepository;
        this.authorizationService = authorizationService;
        this.analyticsService = analyticsService;
        this.adherenceService = adherenceService;
        this.medicationRepository = medicationRepository;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PatientMonitoringSummaryResponse> patients(SmartMedUserDetails viewer,
                                                            RelationshipType relationshipType) {
        List<User> patients = authorizationService.activePatients(viewer, relationshipType);
        List<Long> patientIds = patients.stream().map(User::getId).toList();
        Map<Long, AnalyticsSummaryResponse> summaries = analyticsService.summariesForPatients(patientIds);
        return patients.stream()
                .map(patient -> PatientMonitoringSummaryResponse.from(patient, summaries.get(patient.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonitoringDoseResponse> today(SmartMedUserDetails viewer, Long patientId,
                                               RelationshipType relationshipType) {
        User patient = authorizationService.requireActivePatient(viewer, patientId, relationshipType);
        return adherenceService.todayForAuthorizedPatient(patient.getId()).stream()
                .map(MonitoringDoseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientAnalyticsResponse analytics(SmartMedUserDetails viewer, Long patientId,
                                               LocalDate from, LocalDate to,
                                               RelationshipType relationshipType) {
        User patient = authorizationService.requireActivePatient(viewer, patientId, relationshipType);
        AnalyticsSummaryResponse summary = analyticsService.summaryForPatient(patient.getId(), from, to);
        return new PatientAnalyticsResponse(
                summary,
                analyticsService.dailyForPatient(patient.getId(), summary.from(), summary.to()),
                analyticsService.medicationsForPatient(patient.getId(), summary.from(), summary.to())
        );
    }

    @Transactional(readOnly = true)
    public PatientOverviewResponse overview(SmartMedUserDetails viewer, Long patientId) {
        User patient = authorizationService.requireActivePatient(viewer, patientId, RelationshipType.DOCTOR);
        LocalDate today = LocalDate.now(clock);
        List<Medication> currentMedications = medicationRepository
                .findAllByPatientIdAndActiveTrueOrderByCreatedAtDesc(patient.getId()).stream()
                .filter(medication -> !medication.getStartDate().isAfter(today))
                .filter(medication -> medication.getEndDate() == null || !medication.getEndDate().isBefore(today))
                .toList();
        Set<Long> currentMedicationIds = currentMedications.stream()
                .map(Medication::getId)
                .collect(Collectors.toSet());
        List<ScheduleResponse> activeSchedules = scheduleRepository.findActiveSchedulesForPatient(patient.getId()).stream()
                .filter(schedule -> currentMedicationIds.contains(schedule.getMedication().getId()))
                .filter(schedule -> appliesToday(schedule, today))
                .map(ScheduleResponse::from)
                .toList();
        return new PatientOverviewResponse(
                patient.getId(), patient.getFullName(), patient.getEmail(),
                currentMedications.stream().map(MedicationResponse::from).toList(),
                activeSchedules,
                analyticsService.summaryForPatient(patient.getId(), null, null)
        );
    }

    private static boolean appliesToday(MedicationSchedule schedule, LocalDate today) {
        return !schedule.getStartDate().isAfter(today)
                && (schedule.getEndDate() == null || !schedule.getEndDate().isBefore(today))
                && Boolean.TRUE.equals(schedule.getActive());
    }
}
