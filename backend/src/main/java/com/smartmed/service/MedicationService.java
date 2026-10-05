package com.smartmed.service;

import com.smartmed.dto.request.CreateMedicationRequest;
import com.smartmed.dto.request.UpdateMedicationRequest;
import com.smartmed.dto.response.MedicationResponse;
import com.smartmed.entity.Medication;
import com.smartmed.entity.Role;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.UserRepository;
import com.smartmed.security.SmartMedUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;

    public MedicationService(MedicationRepository medicationRepository, UserRepository userRepository) {
        this.medicationRepository = medicationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MedicationResponse create(CreateMedicationRequest request, SmartMedUserDetails principal) {
        requirePatient(principal);
        var patient = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Medication medication = new Medication();
        medication.setPatient(patient);
        apply(medication, request.name(), request.dosage(), request.frequency(),
                request.instructions(), request.startDate(), request.endDate());
        return MedicationResponse.from(medicationRepository.save(medication));
    }

    @Transactional(readOnly = true)
    public List<MedicationResponse> list(SmartMedUserDetails principal) {
        requirePatient(principal);
        return medicationRepository.findAllByPatientIdOrderByCreatedAtDesc(principal.getId())
                .stream().map(MedicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MedicationResponse get(Long id, SmartMedUserDetails principal) {
        requirePatient(principal);
        return ownedMedication(id, principal.getId()).map(MedicationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Medication not found"));
    }

    @Transactional
    public MedicationResponse update(Long id, UpdateMedicationRequest request, SmartMedUserDetails principal) {
        requirePatient(principal);
        Medication medication = ownedMedication(id, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medication not found"));
        apply(medication, request.name(), request.dosage(), request.frequency(),
                request.instructions(), request.startDate(), request.endDate());
        return MedicationResponse.from(medicationRepository.save(medication));
    }

    @Transactional
    public void delete(Long id, SmartMedUserDetails principal) {
        requirePatient(principal);
        Medication medication = ownedMedication(id, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medication not found"));
        medicationRepository.delete(medication);
    }

    private java.util.Optional<Medication> ownedMedication(Long id, Long patientId) {
        return medicationRepository.findByIdAndPatientId(id, patientId);
    }

    private static void requirePatient(SmartMedUserDetails principal) {
        if (principal.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can manage medications");
        }
    }

    private static void apply(Medication medication, String name, String dosage, String frequency,
                              String instructions, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        medication.setName(name.trim());
        medication.setDosage(dosage.trim());
        medication.setFrequency(frequency.trim());
        medication.setInstructions(instructions == null || instructions.isBlank() ? null : instructions.trim());
        medication.setStartDate(startDate);
        medication.setEndDate(endDate);
    }
}
