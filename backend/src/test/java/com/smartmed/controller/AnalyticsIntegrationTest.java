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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsIntegrationTest {

    private static final String BASE = "/api/v1/analytics";

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
    void patientCanReadSummaryDailyTrendAndMedicationBreakdown() throws Exception {
        String token = registerAndGetToken("PATIENT");
        long firstMedication = createMedication(token, "Morning medicine");
        createSchedule(token, firstMedication, "FOUR_TIMES_DAILY");
        JsonNode fourDoses = getToday(token);
        mark(token, fourDoses.get(0).path("id").asLong(), "taken");
        mark(token, fourDoses.get(1).path("id").asLong(), "missed");
        mark(token, fourDoses.get(2).path("id").asLong(), "skipped");

        long secondMedication = createMedication(token, "Evening medicine");
        createSchedule(token, secondMedication, "ONCE_DAILY");
        JsonNode oneDose = getToday(token);
        long secondMedicationDose = -1;
        for (JsonNode dose : oneDose) {
            if (dose.path("medicationId").asLong() == secondMedication) {
                secondMedicationDose = dose.path("id").asLong();
            }
        }
        mark(token, secondMedicationDose, "taken");

        String today = LocalDate.now().toString();
        mockMvc.perform(get(BASE + "/summary").param("from", today).param("to", today)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDoses").value(5))
                .andExpect(jsonPath("$.data.taken").value(2))
                .andExpect(jsonPath("$.data.missed").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.pending").value(1))
                .andExpect(jsonPath("$.data.adherencePercentage").value(50.0));

        mockMvc.perform(get(BASE + "/daily").param("from", today).param("to", today)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].date").value(today))
                .andExpect(jsonPath("$.data[0].taken").value(2))
                .andExpect(jsonPath("$.data[0].pending").value(1))
                .andExpect(jsonPath("$.data[0].adherencePercentage").value(50.0));

        mockMvc.perform(get(BASE + "/medications").param("from", today).param("to", today)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].medicationName").value("Evening medicine"))
                .andExpect(jsonPath("$.data[0].taken").value(1))
                .andExpect(jsonPath("$.data[0].adherencePercentage").value(100.0))
                .andExpect(jsonPath("$.data[1].medicationName").value("Morning medicine"))
                .andExpect(jsonPath("$.data[1].pending").value(1));
    }

    @Test
    void pendingDosesDoNotReduceAdherenceAndEmptyDataReturnsZeroes() throws Exception {
        String token = registerAndGetToken("PATIENT");
        createSchedule(token, createMedication(token, "Pending medicine"), "ONCE_DAILY");
        getToday(token);
        String today = LocalDate.now().toString();
        mockMvc.perform(get(BASE + "/summary").param("from", today).param("to", today)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(1))
                .andExpect(jsonPath("$.data.adherencePercentage").value(0.0));

        String yesterday = LocalDate.now().minusDays(1).toString();
        mockMvc.perform(get(BASE + "/summary").param("from", yesterday).param("to", yesterday)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDoses").value(0))
                .andExpect(jsonPath("$.data.adherencePercentage").value(0.0));
        mockMvc.perform(get(BASE + "/daily").param("from", yesterday).param("to", yesterday)
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get(BASE + "/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value(LocalDate.now().minusDays(29).toString()))
                .andExpect(jsonPath("$.data.to").value(LocalDate.now().toString()));
    }

    @Test
    void dateFiltersAreInclusiveAndReversedRangesAreRejected() throws Exception {
        String token = registerAndGetToken("PATIENT");
        createSchedule(token, createMedication(token, "Today only"), "ONCE_DAILY");
        getToday(token);
        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        mockMvc.perform(get(BASE + "/summary").param("from", today).param("to", today)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalDoses").value(1));
        mockMvc.perform(get(BASE + "/summary").param("from", tomorrow).param("to", tomorrow)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalDoses").value(0));
        mockMvc.perform(get(BASE + "/summary").param("from", tomorrow).param("to", today)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void patientOnlySeesAnalyticsForTheirOwnDoseRecords() throws Exception {
        String owner = registerAndGetToken("PATIENT");
        String otherPatient = registerAndGetToken("PATIENT");
        createSchedule(owner, createMedication(owner, "Private medicine"), "ONCE_DAILY");
        getToday(owner);
        String today = LocalDate.now().toString();
        mockMvc.perform(get(BASE + "/summary").param("from", today).param("to", today)
                        .header("Authorization", bearer(otherPatient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalDoses").value(0));
        mockMvc.perform(get(BASE + "/daily").param("from", today).param("to", today)
                        .header("Authorization", bearer(otherPatient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get(BASE + "/medications").param("from", today).param("to", today)
                        .header("Authorization", bearer(otherPatient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void analyticsEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get(BASE + "/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/daily")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/medications")).andExpect(status().isUnauthorized());
    }

    @Test
    void caregiverCannotReadAnalytics() throws Exception {
        assertRoleCannotReadAnalytics("CAREGIVER");
    }

    @Test
    void doctorCannotReadAnalytics() throws Exception {
        assertRoleCannotReadAnalytics("DOCTOR");
    }

    private void assertRoleCannotReadAnalytics(String role) throws Exception {
        String token = registerAndGetToken(role);
        mockMvc.perform(get(BASE + "/summary").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/daily").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/medications").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    private JsonNode getToday(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/adherence/today").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void mark(String token, long doseId, String status) throws Exception {
        if (doseId <= 0) throw new AssertionError("Expected a dose record to mark");
        mockMvc.perform(post("/api/v1/adherence/{id}/{status}", doseId, status)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private long createMedication(String token, String name) throws Exception {
        String body = "{\"name\":\"%s\",\"dosage\":\"1 tablet\",\"frequency\":\"daily\",\"startDate\":\"%s\"}"
                .formatted(name, LocalDate.now());
        MvcResult result = mockMvc.perform(post("/api/v1/medications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void createSchedule(String token, long medicationId, String frequency) throws Exception {
        String body = "{\"frequency\":\"%s\",\"timeOfDay\":\"08:00\",\"startDate\":\"%s\",\"endDate\":null}"
                .formatted(frequency, LocalDate.now());
        mockMvc.perform(post("/api/v1/medications/{id}/schedules", medicationId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private String registerAndGetToken(String role) throws Exception {
        String email = "analytics." + UUID.randomUUID() + "@example.com";
        String body = "{\"fullName\":\"Analytics Test\",\"email\":\"%s\",\"password\":\"SmartMed@123\",\"role\":\"%s\"}"
                .formatted(email, role);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
