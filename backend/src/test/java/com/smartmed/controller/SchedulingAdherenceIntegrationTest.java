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
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
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
    void reversedHistoryDateRangeReturnsBadRequest() throws Exception {
        String token = registerAndGetToken("PATIENT");
        mockMvc.perform(get("/api/v1/adherence/history")
                        .param("from", LocalDate.now().plusDays(1).toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
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
}
