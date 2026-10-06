package com.smartmed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.UserRepository;
import com.smartmed.service.interaction.DrugInteractionProvider;
import com.smartmed.service.interaction.DrugInteractionResult;
import com.smartmed.service.interaction.InteractionSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InteractionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private UserRepository userRepository;
    @MockBean private DrugInteractionProvider provider;

    @BeforeEach
    void cleanDatabase() {
        medicationRepository.deleteAll();
        userRepository.deleteAll();
        reset(provider);
        when(provider.getProviderId()).thenReturn("test-provider");
        when(provider.checkInteractions(anyList())).thenReturn(List.of());
    }

    @Test
    void authenticatedPatientCanCheckOwnMedications() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medicationId = createMedication(token, "Patient medication");

        mockMvc.perform(checkRequest(token, "[%d]".formatted(medicationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_DATA"))
                .andExpect(jsonPath("$.data.checkedMedicationCount").value(1))
                .andExpect(jsonPath("$.data.providerId").value("test-provider"))
                .andExpect(jsonPath("$.data.interactions").isEmpty());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/interactions/check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicationIds\":[1]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void caregiverCannotCheckInteractions() throws Exception {
        String token = registerAndGetToken("CAREGIVER");
        mockMvc.perform(checkRequest(token, "[1]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotCheckInteractions() throws Exception {
        String token = registerAndGetToken("DOCTOR");
        mockMvc.perform(checkRequest(token, "[1]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyMedicationIdsAreRejected() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(checkRequest(token, "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nullMedicationIdsAreRejected() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(checkRequest(token, "null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void duplicateMedicationIdsAreRejected() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medicationId = createMedication(token, "Patient medication");
        mockMvc.perform(checkRequest(token, "[%d,%d]".formatted(medicationId, medicationId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void zeroAndNegativeMedicationIdsAreRejected() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(checkRequest(token, "[0]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(checkRequest(token, "[-1]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(checkRequest(token, "[\"not-an-id\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void medicationSelectionHasAReasonableMaximum() throws Exception {
        String token = registerAndGetToken("PATIENT");
        String ids = IntStream.rangeClosed(1, 21).mapToObj(String::valueOf).collect(Collectors.joining(","));
        mockMvc.perform(checkRequest(token, "[%s]".formatted(ids)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(provider);
    }

    @Test
    void missingMedicationIdIsRejectedWithoutCallingProvider() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(checkRequest(token, "[999999]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        verifyNoInteractions(provider);
    }

    @Test
    void medicationOwnedByAnotherPatientIsRejected() throws Exception {
        String ownerToken = registerAndGetToken("PATIENT");
        String otherToken = registerAndGetToken("PATIENT");
        long medicationId = createMedication(ownerToken, "Private medication");

        mockMvc.perform(checkRequest(otherToken, "[%d]".formatted(medicationId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        verifyNoInteractions(provider);
    }

    @Test
    void mixedOwnedAndForeignMedicationIdsFailAsOneRequest() throws Exception {
        String patientToken = registerAndGetToken("PATIENT");
        String otherPatientToken = registerAndGetToken("PATIENT");
        long ownId = createMedication(patientToken, "Own medication");
        long foreignId = createMedication(otherPatientToken, "Foreign medication");

        mockMvc.perform(checkRequest(patientToken, "[%d,%d]".formatted(ownId, foreignId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        verifyNoInteractions(provider);
    }

    @Test
    void providerReceivesOnlySelectedMedicationNamesInRequestOrder() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long firstId = createMedication(token, "Selected one");
        long secondId = createMedication(token, "Selected two");
        createMedication(token, "Not selected");

        mockMvc.perform(checkRequest(token, "[%d,%d]".formatted(secondId, firstId)))
                .andExpect(status().isOk());

        verify(provider).checkInteractions(List.of("Selected two", "Selected one"));
    }

    @Test
    void providerSuccessIsMappedToResponse() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medicationId = createMedication(token, "Selected medication");
        when(provider.checkInteractions(anyList())).thenReturn(List.of(new DrugInteractionResult(
                "Fixture medication A", "Fixture medication B", InteractionSeverity.MODERATE,
                "Synthetic test fixture only; not clinical interaction data.",
                null, "test-only-fixture", false)));

        mockMvc.perform(checkRequest(token, "[%d]".formatted(medicationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.interactions.length()").value(1))
                .andExpect(jsonPath("$.data.interactions[0].medicationA").value("Fixture medication A"))
                .andExpect(jsonPath("$.data.interactions[0].medicationB").value("Fixture medication B"))
                .andExpect(jsonPath("$.data.interactions[0].severity").value("MODERATE"))
                .andExpect(jsonPath("$.data.interactions[0].description")
                        .value("Synthetic test fixture only; not clinical interaction data."));
    }

    @Test
    void providerNoDataIsNotPresentedAsNoInteractions() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medicationId = createMedication(token, "Patient medication");

        mockMvc.perform(checkRequest(token, "[%d]".formatted(medicationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_DATA"))
                .andExpect(jsonPath("$.data.interactions").isEmpty());
    }

    @Test
    void providerFailureReturnsUnavailableInsteadOfNoData() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medicationId = createMedication(token, "Patient medication");
        doThrow(new IllegalStateException("private provider detail"))
                .when(provider).checkInteractions(anyList());

        MvcResult result = mockMvc.perform(checkRequest(token, "[%d]".formatted(medicationId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERACTION_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.status").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("Interaction checking is currently unavailable"))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(response.toString()).doesNotContain("private provider detail");
    }

    private String registerAndGetToken(String role) throws Exception {
        String email = "interaction." + UUID.randomUUID() + "@example.com";
        String body = """
                {"fullName":"Interaction Test","email":"%s","password":"SmartMed@123","role":"%s"}
                """.formatted(email, role);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        promoteRole(email, role);
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private long createMedication(String token, String name) throws Exception {
        String body = """
                {"name":"%s","dosage":"1 tablet","frequency":"daily","startDate":"2026-01-01"}
                """.formatted(name);
        MvcResult result = mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder checkRequest(
            String token, String idsJson) {
        return post("/api/v1/interactions/check")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicationIds\":%s}".formatted(idsJson));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
    private void promoteRole(String email, String role) {
        if ("PATIENT".equals(role)) return;
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setRole(com.smartmed.entity.Role.valueOf(role));
            userRepository.save(user);
        });
    }

}
