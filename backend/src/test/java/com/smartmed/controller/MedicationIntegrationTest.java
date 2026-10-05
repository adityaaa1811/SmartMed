package com.smartmed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MedicationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        medicationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void patientCanCreateMedication() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(validMedication("Vitamin D")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Vitamin D"))
                .andExpect(jsonPath("$.data.startDate").value("2026-01-10"))
                .andExpect(jsonPath("$.data.patient").doesNotExist());
    }

    @Test
    void patientCanListTheirMedications() throws Exception {
        String token = registerAndGetToken("PATIENT");
        createMedication(token, "Vitamin D");
        createMedication(token, "Iron");
        mockMvc.perform(get("/api/v1/medications").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void patientCanGetUpdateAndDeleteTheirMedication() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long id = createMedication(token, "Vitamin D");

        mockMvc.perform(get("/api/v1/medications/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Vitamin D"));
        mockMvc.perform(put("/api/v1/medications/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(validMedication("Vitamin D3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Vitamin D3"));
        mockMvc.perform(delete("/api/v1/medications/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/medications/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsCannotAccessMedicationEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/medications").contentType(MediaType.APPLICATION_JSON)
                        .content(validMedication("Vitamin D"))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/medications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/medications/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/medications/1").contentType(MediaType.APPLICATION_JSON)
                        .content(validMedication("Vitamin D"))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/medications/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotReadAnotherPatientsMedication() throws Exception {
        String ownerToken = registerAndGetToken("PATIENT");
        String otherToken = registerAndGetToken("PATIENT");
        long id = createMedication(ownerToken, "Private medication");

        mockMvc.perform(get("/api/v1/medications/{id}", id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/medications").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void patientCannotUpdateAnotherPatientsMedication() throws Exception {
        String ownerToken = registerAndGetToken("PATIENT");
        String otherToken = registerAndGetToken("PATIENT");
        long id = createMedication(ownerToken, "Private medication");

        mockMvc.perform(put("/api/v1/medications/{id}", id).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(validMedication("Changed")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/medications/{id}", id).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.data.name").value("Private medication"));
    }

    @Test
    void patientCannotDeleteAnotherPatientsMedication() throws Exception {
        String ownerToken = registerAndGetToken("PATIENT");
        String otherToken = registerAndGetToken("PATIENT");
        long id = createMedication(ownerToken, "Private medication");

        mockMvc.perform(delete("/api/v1/medications/{id}", id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/medications/{id}", id).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void invalidMedicationDataReturnsValidationErrors() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"dosage\":\"\",\"frequency\":\"daily\",\"startDate\":\"2026-02-01\",\"endDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors").isArray());
    }

    @Test
    void caregiverCannotCreateMedication() throws Exception {
        String token = registerAndGetToken("CAREGIVER");
        mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(validMedication("Vitamin D")))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotCreateMedication() throws Exception {
        String token = registerAndGetToken("DOCTOR");
        mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(validMedication("Vitamin D")))
                .andExpect(status().isForbidden());
    }

    private long createMedication(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(validMedication(name)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    private String registerAndGetToken(String role) throws Exception {
        String email = "med." + UUID.randomUUID() + "@example.com";
        String request = "{\"fullName\":\"Medication Test\",\"email\":\"%s\",\"password\":\"SmartMed@123\",\"role\":\"%s\"}"
                .formatted(email, role);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private static String validMedication(String name) {
        return "{\"name\":\"%s\",\"dosage\":\"1 tablet\",\"frequency\":\"Once daily\",\"instructions\":\"Take with food\",\"startDate\":\"2026-01-10\",\"endDate\":\"2026-02-10\"}"
                .formatted(name);
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
