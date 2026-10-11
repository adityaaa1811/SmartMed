package com.smartmed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.repository.DoseRecordRepository;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.MedicationScheduleRepository;
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

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.smartmed.entity.Medication;
import com.smartmed.entity.MedicationSchedule;

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
class SchedulingAdherenceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DoseRecordRepository doseRepository;
    @Autowired private MedicationScheduleRepository scheduleRepository;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        doseRepository.deleteAll();
        scheduleRepository.deleteAll();
        medicationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void patientCanCreateScheduleForOwnMedication() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medicationId = createMedication(token);

        mockMvc.perform(post(schedulesPath(medicationId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(scheduleJson("TWICE_DAILY", LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.medicationId").value(medicationId))
                .andExpect(jsonPath("$.data.frequency").value("TWICE_DAILY"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void patientCanListOwnMedicationSchedules() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medicationId = createMedication(token);
        createSchedule(token, medicationId, "ONCE_DAILY");
        createSchedule(token, medicationId, "TWICE_DAILY");

        mockMvc.perform(get(schedulesPath(medicationId)).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void patientCanRetrieveOwnSchedule() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long scheduleId = createSchedule(token, createMedication(token), "ONCE_DAILY");
        mockMvc.perform(get("/api/v1/schedules/{id}", scheduleId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(scheduleId));
    }

    @Test
    void patientCanUpdateOwnSchedule() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long scheduleId = createSchedule(token, createMedication(token), "ONCE_DAILY");
        mockMvc.perform(put("/api/v1/schedules/{id}", scheduleId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleUpdateJson("THREE_TIMES_DAILY", LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frequency").value("THREE_TIMES_DAILY"))
                .andExpect(jsonPath("$.data.timeOfDay").value("09:30:00"));
    }

    @Test
    void deletingScheduleDeactivatesItAndPreservesDoseHistory() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long scheduleId = createSchedule(token, createMedication(token), "ONCE_DAILY");
        getToday(token);

        mockMvc.perform(delete("/api/v1/schedules/{id}", scheduleId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/schedules/{id}", scheduleId).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/adherence/history").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("CANCELLED"));
    }

    @Test
    void deactivatingMedicationCancelsPendingDosesButPreservesCompletedHistory() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medicationId = createMedication(token);
        createSchedule(token, medicationId, "TWICE_DAILY");
        JsonNode today = getToday(token);
        long completedDoseId = today.get(0).path("id").asLong();
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", completedDoseId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/medications/{id}", medicationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(doseRepository.findById(completedDoseId).orElseThrow().getStatus())
                .isEqualTo(com.smartmed.entity.DoseStatus.TAKEN);
        org.assertj.core.api.Assertions.assertThat(doseRepository.findAll().stream()
                .filter(dose -> !dose.getId().equals(completedDoseId))
                .map(com.smartmed.entity.DoseRecord::getStatus))
                .containsExactly(com.smartmed.entity.DoseStatus.CANCELLED);

        String todayDate = LocalDate.now().toString();
        mockMvc.perform(get("/api/v1/analytics/summary").param("from", todayDate).param("to", todayDate)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDoses").value(1))
                .andExpect(jsonPath("$.data.taken").value(1))
                .andExpect(jsonPath("$.data.pending").value(0));
    }

    @Test
    void invalidScheduleDataReturnsValidationErrors() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medicationId = createMedication(token);
        mockMvc.perform(post(schedulesPath(medicationId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"09:00\",\"startDate\":\"2026-10-12\",\"endDate\":\"2026-10-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void patientCannotViewAnotherPatientsSchedules() throws Exception {
        String owner = registerAndGetToken("PATIENT");
        String other = registerAndGetToken("PATIENT");
        long medicationId = createMedication(owner);
        long scheduleId = createSchedule(owner, medicationId, "ONCE_DAILY");
        mockMvc.perform(get(schedulesPath(medicationId)).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/schedules/{id}", scheduleId).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientCannotModifyAnotherPatientsSchedule() throws Exception {
        String owner = registerAndGetToken("PATIENT");
        String other = registerAndGetToken("PATIENT");
        long scheduleId = createSchedule(owner, createMedication(owner), "ONCE_DAILY");
        mockMvc.perform(put("/api/v1/schedules/{id}", scheduleId).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleUpdateJson("TWICE_DAILY", LocalDate.now())))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientCannotDeleteAnotherPatientsSchedule() throws Exception {
        String owner = registerAndGetToken("PATIENT");
        String other = registerAndGetToken("PATIENT");
        long scheduleId = createSchedule(owner, createMedication(owner), "ONCE_DAILY");
        mockMvc.perform(delete("/api/v1/schedules/{id}", scheduleId).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/schedules/{id}", scheduleId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void caregiverCannotManageSchedulesOrAdherence() throws Exception {
        String token = registerAndGetToken("CAREGIVER");
        mockMvc.perform(get("/api/v1/adherence/today").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(schedulesPath(1L)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("ONCE_DAILY", LocalDate.now())))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotManageSchedulesOrAdherence() throws Exception {
        String token = registerAndGetToken("DOCTOR");
        mockMvc.perform(get("/api/v1/adherence/today").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(schedulesPath(1L)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("ONCE_DAILY", LocalDate.now())))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedScheduleAndAdherenceRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/adherence/today")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/adherence/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/medications/1/schedules")).andExpect(status().isUnauthorized());
    }

    @Test
    void todayReturnsMaterializedPendingSlotsWithoutCreatingFutureRecords() throws Exception {
        String token = registerAndGetToken("PATIENT");
        createSchedule(token, createMedication(token), "TWICE_DAILY");

        JsonNode doses = getToday(token);
        org.assertj.core.api.Assertions.assertThat(doses.size()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(doses.get(0).path("status").asText()).isEqualTo("PENDING");
        org.assertj.core.api.Assertions.assertThat(doseRepository.count()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(doses.get(0).path("medicationName").asText()).isEqualTo("Test Medication");
    }

    @Test
    void patientCanMarkDoseTakenAndTakenAtIsSet() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long doseId = createTodayDose(token);
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("TAKEN"))
                .andExpect(jsonPath("$.data.takenAt").isNotEmpty());
    }

    @Test
    void patientCanMarkDoseMissed() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long doseId = createTodayDose(token);
        mockMvc.perform(post("/api/v1/adherence/{id}/missed", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("MISSED"))
                .andExpect(jsonPath("$.data.takenAt").doesNotExist());
    }

    @Test
    void patientCanMarkDoseSkipped() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long doseId = createTodayDose(token);
        mockMvc.perform(post("/api/v1/adherence/{id}/skipped", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SKIPPED"));
    }

    @Test
    void doseActionsEnforceOwnershipForEveryStatus() throws Exception {
        String owner = registerAndGetToken("PATIENT");
        String other = registerAndGetToken("PATIENT");
        long doseId = createTodayDose(owner);
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/adherence/{id}/missed", doseId).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/adherence/{id}/skipped", doseId).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/adherence/today").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void completedDoseCannotTransitionAgain() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long doseId = createTodayDose(token);
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/adherence/{id}/missed", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_DOSE_TRANSITION"));
    }

    @Test
    void patientCanRetrieveDateFilteredAdherenceHistory() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long doseId = createTodayDose(token);
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/v1/adherence/history").param("from", today).param("to", today)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("TAKEN"));
    }

    @Test
    void historyDefaultsToFirstPageAndAcceptsMaximumPageSize() throws Exception {
        String token = registerAndGetToken("PATIENT");
        createTodayDose(token);
        mockMvc.perform(get("/api/v1/adherence/history").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/adherence/history").param("page", "0").param("pageSize", "100")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void defaultHistoryPageReturnsAtMost50Records() throws Exception {
        String token = registerAndGetToken("PATIENT");
        for (int index = 0; index < 13; index++) {
            createSchedule(token, createMedication(token), "FOUR_TIMES_DAILY");
        }
        org.assertj.core.api.Assertions.assertThat(getToday(token).size()).isEqualTo(52);

        mockMvc.perform(get("/api/v1/adherence/history").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(50));
        mockMvc.perform(get("/api/v1/adherence/history").param("page", "1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void historyRejectsPageSizeAboveMaximum() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(get("/api/v1/adherence/history").param("pageSize", "101")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ADHERENCE_HISTORY_PAGE"));
    }

    @Test
    void historyReturnsEmptyForEmptyAndOutOfRangePages() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(get("/api/v1/adherence/history").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        createTodayDose(token);
        mockMvc.perform(get("/api/v1/adherence/history").param("page", "1").param("pageSize", "1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void reversedHistoryDateRangeReturnsBadRequest() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(get("/api/v1/adherence/history")
                        .param("from", LocalDate.now().plusDays(1).toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
    }


    @Test
    void concurrentTodayRequestsMaterializeEachScheduleSlotOnce() throws Exception {
        String token = registerAndGetToken("PATIENT");
        createSchedule(token, createMedication(token), "ONCE_DAILY");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> mockMvc.perform(get("/api/v1/adherence/today")
                            .header("Authorization", bearer(token))).andReturn().getResponse().getStatus());
            Future<Integer> second = executor.submit(() -> mockMvc.perform(get("/api/v1/adherence/today")
                            .header("Authorization", bearer(token))).andReturn().getResponse().getStatus());
            org.assertj.core.api.Assertions.assertThat(first.get()).isEqualTo(200);
            org.assertj.core.api.Assertions.assertThat(second.get()).isEqualTo(200);
        } finally { executor.shutdownNow(); }
        org.assertj.core.api.Assertions.assertThat(doseRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentDoseTransitionsCannotOverwriteTerminalState() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long doseId = createTodayDose(token);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> taken = executor.submit(() -> mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId)
                            .header("Authorization", bearer(token))).andReturn().getResponse().getStatus());
            Future<Integer> missed = executor.submit(() -> mockMvc.perform(post("/api/v1/adherence/{id}/missed", doseId)
                            .header("Authorization", bearer(token))).andReturn().getResponse().getStatus());
            java.util.List<Integer> statuses = java.util.List.of(taken.get(), missed.get());
            org.assertj.core.api.Assertions.assertThat(statuses).contains(200, 409);
        } finally { executor.shutdownNow(); }
        org.assertj.core.api.Assertions.assertThat(doseRepository.findById(doseId).orElseThrow().getStatus())
                .isIn(com.smartmed.entity.DoseStatus.TAKEN, com.smartmed.entity.DoseStatus.MISSED);
    }

    @Test
    void scheduleCreationWithAndWithoutDoseSize() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedication(token);

        // With explicit doseSize
        String withDose = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":2}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(withDose))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.doseSize").value(2))
                .andExpect(jsonPath("$.data.doseSizeVerified").value(true));

        // Without doseSize (unverified legacy)
        String withoutDose = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"12:00\",\"startDate\":\"" + LocalDate.now() + "\"}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(withoutDose))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.doseSize").doesNotExist())
                .andExpect(jsonPath("$.data.doseSizeVerified").value(false));
    }

    @Test
    void scheduleCreationRejectsInvalidDoseSize() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedication(token);

        String zero = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":0}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(zero))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        String negative = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":-2}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(negative))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void scheduleUpdatePreservesOmittedDoseSizeOrUpdatesWhenSupplied() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedication(token);

        // Create with doseSize = 3
        String withDose = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":3}";
        MvcResult created = mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(withDose))
                .andExpect(status().isCreated()).andReturn();
        long schedId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Update omitting doseSize -> preserves doseSize = 3, doseSizeVerified = true
        String updateOmitted = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"09:00\",\"startDate\":\"" + LocalDate.now() + "\",\"active\":true}";
        mockMvc.perform(put("/api/v1/schedules/{id}", schedId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(updateOmitted))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doseSize").value(3))
                .andExpect(jsonPath("$.data.doseSizeVerified").value(true));

        // Update supplying doseSize = 5 -> updates to 5
        String updateSupplied = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"09:00\",\"startDate\":\"" + LocalDate.now() + "\",\"active\":true,\"doseSize\":5}";
        mockMvc.perform(put("/api/v1/schedules/{id}", schedId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(updateSupplied))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doseSize").value(5))
                .andExpect(jsonPath("$.data.doseSizeVerified").value(true));
    }

    @Test
    void verifiedScheduleDeductsExactInventoryOnTaken() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedicationWithInventory(token, 20L, "tablets");

        // Schedule with doseSize = 3
        String schedReq = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":3}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(schedReq))
                .andExpect(status().isCreated());

        long doseId = getToday(token).get(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TAKEN"))
                .andExpect(jsonPath("$.data.doseSize").value(3));

        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getQuantity()).isEqualTo(17L);
    }

    @Test
    void unverifiedScheduleTransitionsToTakenWithoutDeductingInventory() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedicationWithInventory(token, 20L, "tablets");

        // Schedule without doseSize (unverified)
        String schedReq = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\"}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(schedReq))
                .andExpect(status().isCreated());

        long doseId = getToday(token).get(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TAKEN"));

        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getQuantity()).isEqualTo(20L); // Untouched!
    }

    @Test
    void insufficientInventoryRejectsTakenAndPreservesDoseStatusAndQuantity() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedicationWithInventory(token, 2L, "tablets");

        // Schedule with doseSize = 5 (exceeds inventory 2)
        String schedReq = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":5}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(schedReq))
                .andExpect(status().isCreated());

        long doseId = getToday(token).get(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_INVENTORY"));

        // Both dose status and inventory are preserved
        assertThat(doseRepository.findById(doseId).orElseThrow().getStatus()).isEqualTo(com.smartmed.entity.DoseStatus.PENDING);
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity()).isEqualTo(2L);
    }

    @Test
    void verifiedScheduleWithInvalidQuantityFailsSafely() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedicationWithInventory(token, 20L, "tablets");

        String schedReq = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":2}";
        MvcResult sRes = mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(schedReq))
                .andExpect(status().isCreated()).andReturn();
        long schedId = objectMapper.readTree(sRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Corrupt schedule to simulate verified=true with doseSize=null
        MedicationSchedule sched = scheduleRepository.findById(schedId).orElseThrow();
        sched.setDoseSize(null);
        sched.setDoseSizeVerified(true);
        scheduleRepository.save(sched);

        long doseId = getToday(token).get(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_DOSE_QUANTITY"));

        assertThat(doseRepository.findById(doseId).orElseThrow().getStatus()).isEqualTo(com.smartmed.entity.DoseStatus.PENDING);
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity()).isEqualTo(20L);
    }

    @Test
    void repeatedTakenRequestDoesNotDeductTwice() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedicationWithInventory(token, 20L, "tablets");

        String schedReq = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":4}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(schedReq))
                .andExpect(status().isCreated());

        long doseId = getToday(token).get(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity()).isEqualTo(16L);

        // Repeat request
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId).header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_DOSE_TRANSITION"));

        // Quantity remains 16
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity()).isEqualTo(16L);
    }

    @Test
    void missedAndSkippedTransitionsDoNotDeductInventory() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedicationWithInventory(token, 20L, "tablets");

        // Two schedules for two doses
        String sched1 = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":5}";
        String sched2 = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"20:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":5}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(sched1)).andExpect(status().isCreated());
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(sched2)).andExpect(status().isCreated());

        JsonNode todayDoses = getToday(token);
        long dose1 = todayDoses.get(0).path("id").asLong();
        long dose2 = todayDoses.get(1).path("id").asLong();

        mockMvc.perform(post("/api/v1/adherence/{id}/missed", dose1).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity()).isEqualTo(20L);

        mockMvc.perform(post("/api/v1/adherence/{id}/skipped", dose2).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity()).isEqualTo(20L);
    }

    @Test
    void concurrentRequestsForSameDoseDeductOnlyOnce() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedicationWithInventory(token, 50L, "tablets");

        String schedReq = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"08:00\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":5}";
        mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(schedReq)).andExpect(status().isCreated());

        long doseId = getToday(token).get(0).path("id").asLong();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId)
                                    .header("Authorization", bearer(token)))
                            .andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1); // Exactly one succeeded
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity()).isEqualTo(45L); // Deducted exactly once
    }

    @Test
    void concurrentDifferentDosesForSameMedicationPreserveConsistency() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long medId = createMedicationWithInventory(token, 20L, "tablets");

        // 4 schedules at different times
        for (String time : java.util.List.of("07:00", "12:00", "17:00", "21:00")) {
            String s = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"" + time + "\",\"startDate\":\"" + LocalDate.now() + "\",\"doseSize\":5}";
            mockMvc.perform(post(schedulesPath(medId)).header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(s)).andExpect(status().isCreated());
        }

        JsonNode todayDoses = getToday(token);
        assertThat(todayDoses.size()).isEqualTo(4);

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long dId = todayDoses.get(i).path("id").asLong();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/adherence/{id}/taken", dId)
                                    .header("Authorization", bearer(token)))
                            .andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(4);
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity()).isEqualTo(0L);
    }

    private long createMedicationWithInventory(String token, long quantity, String unit) throws Exception {
        String date = LocalDate.now().toString();
        String body = "{\"name\":\"Inventory Medication\",\"dosage\":\"1 tablet\",\"frequency\":\"daily\",\"startDate\":\"%s\",\"quantity\":%d,\"unit\":\"%s\"}"
                .formatted(date, quantity, unit);
        MvcResult result = mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createTodayDose(String token) throws Exception {
        createSchedule(token, createMedication(token), "ONCE_DAILY");
        return getToday(token).get(0).path("id").asLong();
    }

    private JsonNode getToday(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/adherence/today").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private long createMedication(String token) throws Exception {
        String date = LocalDate.now().toString();
        String body = "{\"name\":\"Test Medication\",\"dosage\":\"1 tablet\",\"frequency\":\"daily\",\"startDate\":\"%s\"}"
                .formatted(date);
        MvcResult result = mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createSchedule(String token, long medicationId, String frequency) throws Exception {
        MvcResult result = mockMvc.perform(post(schedulesPath(medicationId)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(scheduleJson(frequency, LocalDate.now())))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String registerAndGetToken(String role) throws Exception {
        String email = "schedule." + UUID.randomUUID() + "@example.com";
        String body = "{\"fullName\":\"Schedule Test\",\"email\":\"%s\",\"password\":\"SmartMed@123\",\"role\":\"%s\"}"
                .formatted(email, role);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        promoteRole(email, role);
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private static String scheduleJson(String frequency, LocalDate start) {
        return "{\"frequency\":\"%s\",\"timeOfDay\":\"08:00\",\"startDate\":\"%s\",\"endDate\":null}"
                .formatted(frequency, start);
    }

    private static String scheduleUpdateJson(String frequency, LocalDate start) {
        return "{\"frequency\":\"%s\",\"timeOfDay\":\"09:30\",\"startDate\":\"%s\",\"endDate\":null,\"active\":true}"
                .formatted(frequency, start);
    }

    private static String schedulesPath(Long medicationId) {
        return "/api/v1/medications/" + medicationId + "/schedules";
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
