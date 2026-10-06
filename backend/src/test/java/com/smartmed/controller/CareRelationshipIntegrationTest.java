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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareRelationshipIntegrationTest {

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
    void patientCanCreateCaregiverAndDoctorRelationshipsByEmail() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        Account doctor = register("DOCTOR");

        long caregiverRelationship = createRelationship(patient, caregiver, "CAREGIVER");
        long doctorRelationship = createRelationship(patient, doctor, "DOCTOR");

        mockMvc.perform(get("/api/v1/relationships").header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
        mockMvc.perform(get("/api/v1/relationships").header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(caregiverRelationship));
        mockMvc.perform(get("/api/v1/relationships").header("Authorization", bearer(doctor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(doctorRelationship));
    }

    @Test
    void relationshipCreationRejectsWrongRolesAndSelfLinks() throws Exception {
        Account patient = register("PATIENT");
        Account anotherPatient = register("PATIENT");
        mockMvc.perform(post("/api/v1/relationships").header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(anotherPatient.email(), "CAREGIVER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RELATIONSHIP_REQUEST"));
        mockMvc.perform(post("/api/v1/relationships").header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(patient.email(), "DOCTOR")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/relationships").header("Authorization", bearer(register("CAREGIVER").token()))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(patient.email(), "CAREGIVER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/relationships").header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relatedUserEmail\":\"not-an-email\",\"relationshipType\":\"DOCTOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void duplicatePendingAndActiveRelationshipsReturnConflict() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        long id = createRelationship(patient, caregiver, "CAREGIVER");

        mockMvc.perform(post("/api/v1/relationships").header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(caregiver.email(), "CAREGIVER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RELATIONSHIP_CONFLICT"));

        mockMvc.perform(post("/api/v1/relationships/{id}/accept", id)
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/relationships").header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(caregiver.email(), "CAREGIVER")))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyRelatedUserCanAcceptPendingRelationship() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        Account otherCaregiver = register("CAREGIVER");
        long id = createRelationship(patient, caregiver, "CAREGIVER");

        mockMvc.perform(post("/api/v1/relationships/{id}/accept", id)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/relationships/{id}/accept", id)
                        .header("Authorization", bearer(otherCaregiver.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/relationships/{id}/accept", id)
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(post("/api/v1/relationships/{id}/accept", id)
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isConflict());
    }

    @Test
    void relatedUserCanRejectAndRejectedRequestIsRevokedAndMayBeRecreated() throws Exception {
        Account patient = register("PATIENT");
        Account doctor = register("DOCTOR");
        long id = createRelationship(patient, doctor, "DOCTOR");

        mockMvc.perform(post("/api/v1/relationships/{id}/reject", id)
                        .header("Authorization", bearer(doctor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
        mockMvc.perform(post("/api/v1/relationships").header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(doctor.email(), "DOCTOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void onlyPatientOwnerCanRevokeAnActiveRelationship() throws Exception {
        Account patient = register("PATIENT");
        Account otherPatient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        long id = createRelationship(patient, caregiver, "CAREGIVER");
        mockMvc.perform(post("/api/v1/relationships/{id}/accept", id)
                .header("Authorization", bearer(caregiver.token()))).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/relationships/{id}", id)
                        .header("Authorization", bearer(otherPatient.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/relationships/{id}", id)
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/relationships/{id}", id)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/relationships").header("Authorization", bearer(patient.token())))
                .andExpect(jsonPath("$.data[0].status").value("REVOKED"));
        mockMvc.perform(delete("/api/v1/relationships/{id}", id)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isConflict());
    }

    @Test
    void pendingRequestCannotBeRevokedByPatientAndUnauthenticatedCannotList() throws Exception {
        Account patient = register("PATIENT");
        Account doctor = register("DOCTOR");
        long id = createRelationship(patient, doctor, "DOCTOR");
        mockMvc.perform(delete("/api/v1/relationships/{id}", id)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/relationships")).andExpect(status().isUnauthorized());
    }

    private long createRelationship(Account patient, Account related, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/relationships")
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(related.email(), type)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private Account register(String role) throws Exception {
        String email = "phase6." + role.toLowerCase() + "." + UUID.randomUUID() + "@example.com";
        String body = "{\"fullName\":\"Phase Six User\",\"email\":\"%s\",\"password\":\"SmartMed@123\",\"role\":\"%s\"}"
                .formatted(email, role);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new Account(data.path("accessToken").asText(), email, data.path("user").path("id").asLong());
    }

    private static String createBody(String email, String type) {
        return "{\"relatedUserEmail\":\"%s\",\"relationshipType\":\"%s\"}".formatted(email, type);
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Account(String token, String email, long id) { }
}
