package com.smartmed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.repository.CareRelationshipRepository;
import com.smartmed.repository.DoseRecordRepository;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.MedicationScheduleRepository;
import com.smartmed.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitoringIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CareRelationshipRepository relationshipRepository;
    @Autowired private DoseRecordRepository doseRepository;
    @Autowired private MedicationScheduleRepository scheduleRepository;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        relationshipRepository.deleteAll();
        doseRepository.deleteAll();
        scheduleRepository.deleteAll();
        medicationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void cleanRelationships() {
        relationshipRepository.deleteAll();
    }

    @Test
    void caregiverSeesOnlyPatientsWithActiveCaregiverConsent() throws Exception {
        Account patientA = register("PATIENT");
        Account patientB = register("PATIENT");
        Account caregiverA = register("CAREGIVER");
        Account caregiverB = register("CAREGIVER");
        long relationshipA = requestRelationship(patientA, caregiverA, "CAREGIVER");
        long relationshipB = requestRelationship(patientB, caregiverB, "CAREGIVER");
        accept(relationshipA, caregiverA);
        accept(relationshipB, caregiverB);

        mockMvc.perform(get("/api/v1/caregiver/patients").header("Authorization", bearer(caregiverA.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].patientId").value(patientA.id()))
                .andExpect(jsonPath("$.data[0].patientEmail").value(patientA.email()));
        mockMvc.perform(get("/api/v1/caregiver/patients/{id}/today", patientB.id())
                        .header("Authorization", bearer(caregiverA.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void caregiverCanReadSavedTodayDosesButCannotMaterializeOrChangeThem() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        long relationshipId = requestRelationship(patient, caregiver, "CAREGIVER");
        accept(relationshipId, caregiver);
        long medicationId = createMedication(patient);
        createSchedule(patient, medicationId);

        mockMvc.perform(get("/api/v1/caregiver/patients/{id}/today", patient.id())
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        org.assertj.core.api.Assertions.assertThat(doseRepository.count()).isZero();

        long doseId = materializeToday(patient);
        mockMvc.perform(get("/api/v1/caregiver/patients/{id}/today", patient.id())
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].doseId").value(doseId))
                .andExpect(jsonPath("$.data[0].medicationName").value("Monitoring medication"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId)
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/adherence/today").header("Authorization", bearer(patient.token())))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void caregiverAnalyticsRequireActiveRelationshipAndSupportDateFilters() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        Account otherCaregiver = register("CAREGIVER");
        long relationshipId = requestRelationship(patient, caregiver, "CAREGIVER");
        accept(relationshipId, caregiver);
        long doseId = getPatientTodayDose(patient);
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk());

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/v1/caregiver/patients/{id}/analytics", patient.id())
                        .param("from", today).param("to", today)
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.taken").value(1))
                .andExpect(jsonPath("$.data.summary.adherencePercentage").value(100.0))
                .andExpect(jsonPath("$.data.daily.length()").value(1));
        mockMvc.perform(get("/api/v1/caregiver/patients/{id}/analytics", patient.id())
                        .header("Authorization", bearer(otherCaregiver.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void doctorSeesOnlyConnectedPatientsAndCannotOpenUnrelatedOverview() throws Exception {
        Account patientA = register("PATIENT");
        Account patientB = register("PATIENT");
        Account doctorA = register("DOCTOR");
        Account doctorB = register("DOCTOR");
        long relationshipA = requestRelationship(patientA, doctorA, "DOCTOR");
        long relationshipB = requestRelationship(patientB, doctorB, "DOCTOR");
        accept(relationshipA, doctorA);
        accept(relationshipB, doctorB);

        mockMvc.perform(get("/api/v1/doctor/patients").header("Authorization", bearer(doctorA.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].patientId").value(patientA.id()));
        mockMvc.perform(get("/api/v1/doctor/patients/{id}", patientB.id())
                        .header("Authorization", bearer(doctorA.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void doctorOverviewContainsCurrentMedicationSchedulesAndNoAuthenticationData() throws Exception {
        Account patient = register("PATIENT");
        Account doctor = register("DOCTOR");
        long relationshipId = requestRelationship(patient, doctor, "DOCTOR");
        accept(relationshipId, doctor);
        long medicationId = createMedication(patient);
        createSchedule(patient, medicationId);
        materializeToday(patient);

        mockMvc.perform(get("/api/v1/doctor/patients/{id}", patient.id())
                        .header("Authorization", bearer(doctor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientName").value("Phase Six User"))
                .andExpect(jsonPath("$.data.patientEmail").value(patient.email()))
                .andExpect(jsonPath("$.data.medications.length()").value(1))
                .andExpect(jsonPath("$.data.medications[0].name").value("Monitoring medication"))
                .andExpect(jsonPath("$.data.activeSchedules.length()").value(1))
                .andExpect(jsonPath("$.data.adherenceSummary.pending").value(1))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.medications[0].patient").doesNotExist());
    }

    @Test
    void doctorCanReadTodayAndAnalyticsButCannotChangeDoseStatus() throws Exception {
        Account patient = register("PATIENT");
        Account doctor = register("DOCTOR");
        long relationshipId = requestRelationship(patient, doctor, "DOCTOR");
        accept(relationshipId, doctor);
        long doseId = getPatientTodayDose(patient);

        mockMvc.perform(get("/api/v1/doctor/patients/{id}/today", patient.id())
                        .header("Authorization", bearer(doctor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].doseId").value(doseId))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
        mockMvc.perform(post("/api/v1/adherence/{id}/missed", doseId)
                        .header("Authorization", bearer(doctor.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/doctor/patients/{id}/analytics", patient.id())
                        .header("Authorization", bearer(doctor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.pending").value(1))
                .andExpect(jsonPath("$.data.medications[0].medicationName").value("Monitoring medication"));
    }

    @Test
    void rolesCannotCrossMonitoringAreasAndPatientsCannotCallEitherDashboard() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        Account doctor = register("DOCTOR");
        mockMvc.perform(get("/api/v1/caregiver/patients").header("Authorization", bearer(patient.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/doctor/patients").header("Authorization", bearer(patient.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/doctor/patients").header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/caregiver/patients").header("Authorization", bearer(doctor.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/caregiver/patients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/doctor/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void revokedRelationshipImmediatelyRemovesCaregiverAccess() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        long relationshipId = requestRelationship(patient, caregiver, "CAREGIVER");
        accept(relationshipId, caregiver);
        mockMvc.perform(get("/api/v1/caregiver/patients").header("Authorization", bearer(caregiver.token())))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/relationships/{id}", relationshipId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/caregiver/patients").header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/caregiver/patients/{id}/today", patient.id())
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isNotFound());
    }

    private long requestRelationship(Account patient, Account related, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/relationships")
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relatedUserEmail\":\"%s\",\"relationshipType\":\"%s\"}"
                                .formatted(related.email(), type)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void accept(long relationshipId, Account related) throws Exception {
        mockMvc.perform(post("/api/v1/relationships/{id}/accept", relationshipId)
                        .header("Authorization", bearer(related.token())))
                .andExpect(status().isOk());
    }

    private long createMedication(Account patient) throws Exception {
        String body = "{\"name\":\"Monitoring medication\",\"dosage\":\"1 tablet\",\"frequency\":\"daily\",\"startDate\":\"%s\"}"
                .formatted(LocalDate.now());
        MvcResult result = mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void createSchedule(Account patient, long medicationId) throws Exception {
        String body = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"%s\",\"endDate\":null}"
                .formatted(LocalDate.now());
        mockMvc.perform(post("/api/v1/medications/{id}/schedules", medicationId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private long getPatientTodayDose(Account patient) throws Exception {
        long medicationId = createMedication(patient);
        createSchedule(patient, medicationId);
        return materializeToday(patient);
    }

    private long materializeToday(Account patient) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/adherence/today")
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk()).andReturn();
        JsonNode doses = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return doses.get(0).path("id").asLong();
    }

    private Account register(String role) throws Exception {
        String email = "monitor." + UUID.randomUUID() + "@example.com";
        String body = "{\"fullName\":\"Phase Six User\",\"email\":\"%s\",\"password\":\"SmartMed@123\",\"role\":\"%s\"}"
                .formatted(email, role);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new Account(data.path("accessToken").asText(), email, data.path("user").path("id").asLong());
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Account(String token, String email, long id) { }
}
