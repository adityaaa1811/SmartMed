package com.smartmed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.entity.Medication;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.MedicationScheduleRepository;
import com.smartmed.repository.DoseRecordRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired private MedicationScheduleRepository scheduleRepository;
    @Autowired private DoseRecordRepository doseRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        doseRepository.deleteAll();
        scheduleRepository.deleteAll();
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
    void deactivatingMedicationPreservesDoseHistoryAndPreventsNewSchedules() throws Exception {
        String token = registerAndGetToken("PATIENT");
        String start = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        MvcResult created = mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"History medication\",\"dosage\":\"1 tablet\",\"frequency\":\"daily\",\"startDate\":\"" + start + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        long medicationId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(post("/api/v1/medications/{id}/schedules", medicationId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + start + "\"}"))
                .andExpect(status().isCreated());
        MvcResult today = mockMvc.perform(get("/api/v1/adherence/today").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        long doseId = objectMapper.readTree(today.getResponse().getContentAsString()).path("data").get(0).path("id").asLong();

        mockMvc.perform(delete("/api/v1/medications/{id}", medicationId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(medicationRepository.findById(medicationId).orElseThrow().getActive()).isFalse();
        assertThat(scheduleRepository.findAllByMedicationIdAndMedicationPatientIdOrderByTimeOfDayAsc(
                medicationId, medicationRepository.findById(medicationId).orElseThrow().getPatient().getId()).get(0).getActive()).isFalse();
        assertThat(doseRepository.findById(doseId)).isPresent();
        mockMvc.perform(get("/api/v1/medications").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/adherence/history").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(post("/api/v1/medications/{id}/schedules", medicationId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"09:00\",\"startDate\":\"" + start + "\"}"))
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

    @Test
    void patientCanRefillMedicationSuccessfully() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long id = createMedicationWithInventory(token, "Metformin", 10L, "tablets");

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("Metformin"))
                .andExpect(jsonPath("$.data.quantity").value(40))
                .andExpect(jsonPath("$.data.unit").value("tablets"));

        assertThat(medicationRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(40L);
    }

    @Test
    void refillRejectsZeroOrNegativeAmounts() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long id = createMedicationWithInventory(token, "Aspirin", 10L, "tablets");

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(medicationRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(10L);
    }

    @Test
    void refillRejectsMissingAndMalformedBodies() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long id = createMedicationWithInventory(token, "Aspirin", 10L, "tablets");

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void refillReturnsNotFoundForMissingMedication() throws Exception {
        String token = registerAndGetToken("PATIENT");

        mockMvc.perform(post("/api/v1/medications/{id}/refill", 999999L)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":30}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void refillEnforcesOwnershipBetweenPatients() throws Exception {
        String ownerToken = registerAndGetToken("PATIENT");
        String otherToken = registerAndGetToken("PATIENT");
        long id = createMedicationWithInventory(ownerToken, "Private Med", 15L, "pills");

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":30}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(medicationRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(15L);
    }

    @Test
    void refillRejectsCaregiverDoctorAndUnauthenticated() throws Exception {
        String patientToken = registerAndGetToken("PATIENT");
        String caregiverToken = registerAndGetToken("CAREGIVER");
        String doctorToken = registerAndGetToken("DOCTOR");
        long id = createMedicationWithInventory(patientToken, "Daily Vitamin", 5L, "capsules");

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(caregiverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void refillRejectsLongOverflow() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long id = createMedicationWithInventory(token, "Bulk Med", 0L, "units");

        Medication med = medicationRepository.findById(id).orElseThrow();
        med.setQuantity(Long.MAX_VALUE - 5L);
        medicationRepository.save(med);

        mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVENTORY_OVERFLOW"));

        assertThat(medicationRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(Long.MAX_VALUE - 5L);
    }

    @Test
    void concurrentRefillRequestsPreserveInventoryConsistency() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long id = createMedicationWithInventory(token, "Concurrent Med", 0L, "tablets");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/medications/{id}/refill", id)
                                    .header("Authorization", bearer(token))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"amount\":10}"))
                            .andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);
        Medication updated = medicationRepository.findById(id).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(100L);
    }

    private long createMedication(String token, String name) throws Exception {
        return createMedicationWithInventory(token, name, 0L, null);
    }

    private long createMedicationWithInventory(String token, String name, Long quantity, String unit) throws Exception {
        String content = validMedicationWithInventory(name, quantity, unit);
        MvcResult result = mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(content))
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
        promoteRole(email, role);
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private static String validMedication(String name) {
        return "{\"name\":\"%s\",\"dosage\":\"1 tablet\",\"frequency\":\"Once daily\",\"instructions\":\"Take with food\",\"startDate\":\"2026-01-10\",\"endDate\":\"2026-02-10\"}"
                .formatted(name);
    }

    private static String validMedicationWithInventory(String name, Long quantity, String unit) {
        if (unit != null) {
            return "{\"name\":\"%s\",\"dosage\":\"1 tablet\",\"frequency\":\"Once daily\",\"instructions\":\"Take with food\",\"startDate\":\"2026-01-10\",\"endDate\":\"2026-02-10\",\"quantity\":%d,\"unit\":\"%s\"}"
                    .formatted(name, quantity, unit);
        } else {
            return "{\"name\":\"%s\",\"dosage\":\"1 tablet\",\"frequency\":\"Once daily\",\"instructions\":\"Take with food\",\"startDate\":\"2026-01-10\",\"endDate\":\"2026-02-10\",\"quantity\":%d}"
                    .formatted(name, quantity);
        }
    }

    private static String bearer(String token) { return "Bearer " + token; }
    private void promoteRole(String email, String role) {
        if ("PATIENT".equals(role)) return;
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setRole(com.smartmed.entity.Role.valueOf(role));
            userRepository.save(user);
        });
    }

}
