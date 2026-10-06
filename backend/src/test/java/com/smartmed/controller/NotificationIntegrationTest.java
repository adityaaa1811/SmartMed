package com.smartmed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.entity.Notification;
import com.smartmed.entity.NotificationType;
import com.smartmed.repository.CareRelationshipRepository;
import com.smartmed.repository.DoseRecordRepository;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.MedicationScheduleRepository;
import com.smartmed.repository.NotificationRepository;
import com.smartmed.repository.UserRepository;
import com.smartmed.service.interaction.DrugInteractionProvider;
import com.smartmed.service.interaction.DrugInteractionResult;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CareRelationshipRepository relationshipRepository;
    @Autowired private DoseRecordRepository doseRepository;
    @Autowired private MedicationScheduleRepository scheduleRepository;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private UserRepository userRepository;
    @MockBean private DrugInteractionProvider interactionProvider;

    @BeforeEach
    void cleanDatabase() {
        notificationRepository.deleteAll();
        relationshipRepository.deleteAll();
        doseRepository.deleteAll();
        scheduleRepository.deleteAll();
        medicationRepository.deleteAll();
        userRepository.deleteAll();
        reset(interactionProvider);
        when(interactionProvider.getProviderId()).thenReturn("test-provider");
        when(interactionProvider.checkInteractions(anyList())).thenReturn(List.of());
    }

    @Test
    void authenticatedUserCanFetchOwnNotifications() throws Exception {
        Account patient = register("PATIENT");
        long doseId = createTodayDose(patient);
        markMissed(patient, doseId);

        JsonNode items = notificationItems(patient.token(), 0, 10);
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).path("type").asText()).isEqualTo("MISSED_DOSE");
        assertThat(items.get(0).path("relatedEntityId").asLong()).isEqualTo(doseId);
    }

    @Test
    void unauthenticatedUserCannotFetchNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    @Test
    void notificationListsArePrivateToEachUser() throws Exception {
        Account owner = register("PATIENT");
        Account other = register("PATIENT");
        markMissed(owner, createTodayDose(owner));

        assertThat(notificationItems(owner.token(), 0, 10).size()).isEqualTo(1);
        assertThat(notificationItems(other.token(), 0, 10).size()).isZero();
    }

    @Test
    void userCanMarkOwnNotificationReadAndUnread() throws Exception {
        Account patient = register("PATIENT");
        markMissed(patient, createTodayDose(patient));
        long id = notificationItems(patient.token(), 0, 10).get(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/notifications/{id}/read", id).header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.read").value(true));
        assertThat(unreadCount(patient.token())).isZero();
        mockMvc.perform(post("/api/v1/notifications/{id}/unread", id).header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.read").value(false));
        assertThat(unreadCount(patient.token())).isEqualTo(1);
    }

    @Test
    void userCannotMarkAnotherUsersNotificationRead() throws Exception {
        Account owner = register("PATIENT");
        Account other = register("PATIENT");
        markMissed(owner, createTodayDose(owner));
        long id = notificationItems(owner.token(), 0, 10).get(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/notifications/{id}/read", id).header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound());
        assertThat(unreadCount(owner.token())).isEqualTo(1);
    }

    @Test
    void unreadCountOnlyIncludesTheAuthenticatedUsersNotifications() throws Exception {
        Account first = register("PATIENT");
        Account second = register("PATIENT");
        markMissed(first, createTodayDose(first));

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(first.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(1));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(second.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void markAllReadOnlyChangesTheAuthenticatedUsersNotifications() throws Exception {
        Account first = register("PATIENT");
        Account second = register("PATIENT");
        markMissed(first, createTodayDose(first));
        markMissed(second, createTodayDose(second));

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", bearer(first.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(1));
        assertThat(unreadCount(first.token())).isZero();
        assertThat(unreadCount(second.token())).isEqualTo(1);
    }

    @Test
    void notificationListingSupportsUnreadFilteringAndPagination() throws Exception {
        Account patient = register("PATIENT");
        markMissed(patient, createTodayDose(patient));
        markMissed(patient, createTodayDose(patient));

        mockMvc.perform(get("/api/v1/notifications").param("page", "0").param("size", "1")
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get("/api/v1/notifications").param("unreadOnly", "true")
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
        mockMvc.perform(get("/api/v1/notifications").param("size", "51")
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_NOTIFICATION_PAGE"));
    }

    @Test
    void missedDoseCreatesPatientNotification() throws Exception {
        Account patient = register("PATIENT");
        long doseId = createTodayDose(patient);
        markMissed(patient, doseId);

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("MISSED_DOSE"))
                .andExpect(jsonPath("$.data.items[0].message").value("Missed dose recorded for Test Medication."));
    }

    @Test
    void failedDoseTransitionDoesNotCreateNotification() throws Exception {
        Account patient = register("PATIENT");
        long doseId = createTodayDose(patient);
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/adherence/{id}/missed", doseId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isConflict());
        assertThat(notificationItems(patient.token(), 0, 10)).isEmpty();
    }

    @Test
    void repeatedMissedDoseRequestDoesNotDuplicateNotification() throws Exception {
        Account patient = register("PATIENT");
        long doseId = createTodayDose(patient);
        markMissed(patient, doseId);
        mockMvc.perform(post("/api/v1/adherence/{id}/missed", doseId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isConflict());

        assertThat(notificationItems(patient.token(), 0, 10).size()).isEqualTo(1);
    }

    @Test
    void activeCaregiverReceivesConnectedPatientsMissedDoseNotification() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        acceptRelationship(patient, caregiver, "CAREGIVER");

        markMissed(patient, createTodayDose(patient));
        assertThat(countType(notificationItems(caregiver.token(), 0, 10), "MISSED_DOSE")).isEqualTo(1);
    }

    @Test
    void activeDoctorReceivesConnectedPatientsMissedDoseNotification() throws Exception {
        Account patient = register("PATIENT");
        Account doctor = register("DOCTOR");
        acceptRelationship(patient, doctor, "DOCTOR");

        markMissed(patient, createTodayDose(patient));
        assertThat(countType(notificationItems(doctor.token(), 0, 10), "MISSED_DOSE")).isEqualTo(1);
    }

    @Test
    void unrelatedCaregiverReceivesNoPatientDoseNotification() throws Exception {
        Account patient = register("PATIENT");
        Account connected = register("CAREGIVER");
        Account unrelated = register("CAREGIVER");
        acceptRelationship(patient, connected, "CAREGIVER");

        markMissed(patient, createTodayDose(patient));
        assertThat(notificationItems(unrelated.token(), 0, 10)).isEmpty();
    }

    @Test
    void revokingRelationshipStopsFutureMonitoringNotifications() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        long relationshipId = acceptRelationship(patient, caregiver, "CAREGIVER");
        mockMvc.perform(delete("/api/v1/relationships/{id}", relationshipId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isNoContent());

        markMissed(patient, createTodayDose(patient));
        assertThat(countType(notificationItems(caregiver.token(), 0, 10), "MISSED_DOSE")).isZero();
    }

    @Test
    void relationshipRequestAcceptanceAndRevocationGenerateNotifications() throws Exception {
        Account patient = register("PATIENT");
        Account doctor = register("DOCTOR");
        long relationshipId = createRelationship(patient, doctor, "DOCTOR");

        assertThat(countType(notificationItems(doctor.token(), 0, 10), "CARE_RELATIONSHIP")).isEqualTo(1);
        assertThat(notificationItems(patient.token(), 0, 10)).isEmpty();
        mockMvc.perform(post("/api/v1/relationships/{id}/accept", relationshipId)
                        .header("Authorization", bearer(doctor.token())))
                .andExpect(status().isOk());
        assertThat(countType(notificationItems(patient.token(), 0, 10), "CARE_RELATIONSHIP")).isEqualTo(1);
        assertThat(countType(notificationItems(doctor.token(), 0, 10), "CARE_RELATIONSHIP")).isEqualTo(2);

        mockMvc.perform(delete("/api/v1/relationships/{id}", relationshipId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isNoContent());
        assertThat(countType(notificationItems(doctor.token(), 0, 10), "CARE_RELATIONSHIP")).isEqualTo(3);
    }

    @Test
    void relationshipDeclineNotifiesThePatient() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        long relationshipId = createRelationship(patient, caregiver, "CAREGIVER");

        mockMvc.perform(post("/api/v1/relationships/{id}/reject", relationshipId)
                        .header("Authorization", bearer(caregiver.token())))
                .andExpect(status().isOk());
        assertThat(countType(notificationItems(patient.token(), 0, 10), "CARE_RELATIONSHIP")).isEqualTo(1);
    }

    @Test
    void adherenceAttentionUsesAnalyticsThresholdAndDeduplicatesByMonth() throws Exception {
        Account patient = register("PATIENT");
        long medicationId = createMedication(patient);
        createSchedule(patient, medicationId, "THREE_TIMES_DAILY");
        JsonNode doses = today(patient);
        assertThat(doses.size()).isEqualTo(3);

        markMissed(patient, doses.get(0).path("id").asLong());
        markMissed(patient, doses.get(1).path("id").asLong());
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doses.get(2).path("id").asLong())
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk());
        assertThat(countType(notificationItems(patient.token(), 0, 50), "ADHERENCE_ATTENTION")).isEqualTo(1);

        long anotherMedication = createMedication(patient);
        createSchedule(patient, anotherMedication, "ONCE_DAILY");
        JsonNode extraDose = null;
        for (JsonNode dose : today(patient)) {
            if (dose.path("status").asText().equals("PENDING")) {
                extraDose = dose;
                break;
            }
        }
        assertThat(extraDose).isNotNull();
        markMissed(patient, extraDose.path("id").asLong());
        assertThat(countType(notificationItems(patient.token(), 0, 50), "ADHERENCE_ATTENTION")).isEqualTo(1);
    }

    @Test
    void zeroRecordedDosesDoNotCreateAdherenceAttentionOrMarkPendingAsMissed() throws Exception {
        Account patient = register("PATIENT");
        createSchedule(patient, createMedication(patient), "ONCE_DAILY");
        assertThat(today(patient).size()).isEqualTo(1);
        assertThat(notificationItems(patient.token(), 0, 10)).isEmpty();
        assertThat(notificationRepository.findAll().stream()
                .noneMatch(item -> item.getType() == NotificationType.ADHERENCE_ATTENTION
                        || item.getType() == NotificationType.MISSED_DOSE)).isTrue();
    }

    @Test
    void emptyInteractionProviderCreatesNoPersistentInteractionNotification() throws Exception {
        Account patient = register("PATIENT");
        long firstId = createMedication(patient);
        long secondId = createMedication(patient);
        String request = "{\"medicationIds\":[%d,%d]}".formatted(firstId, secondId);

        mockMvc.perform(post("/api/v1/interactions/check").header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NO_DATA"));
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    void providerUnavailableDoesNotBecomeNoDataOrCreateNotification() throws Exception {
        Account patient = register("PATIENT");
        long medicationId = createMedication(patient);
        doThrow(new IllegalStateException("hidden upstream detail"))
                .when(interactionProvider).checkInteractions(anyList());

        mockMvc.perform(post("/api/v1/interactions/check").header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicationIds\":[%d]}".formatted(medicationId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.status").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("Interaction checking is currently unavailable"));
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    void generatedMessagesContainOnlySoftwareEventLanguage() throws Exception {
        Account patient = register("PATIENT");
        Account caregiver = register("CAREGIVER");
        acceptRelationship(patient, caregiver, "CAREGIVER");
        long medicationId = createMedication(patient);
        createSchedule(patient, medicationId, "THREE_TIMES_DAILY");
        JsonNode doses = today(patient);
        markMissed(patient, doses.get(0).path("id").asLong());
        markMissed(patient, doses.get(1).path("id").asLong());
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doses.get(2).path("id").asLong())
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk());

        List<String> forbiddenClaims = List.of("diagnos", "treatment", "stop taking", "change dosage",
                "emergency", "health is at risk", "medically non-compliant", "clinical risk");
        List<String> messages = notificationRepository.findAll().stream()
                .map(Notification::getMessage).map(String::toLowerCase).toList();
        for (String claim : forbiddenClaims) {
            assertThat(messages).noneMatch(message -> message.contains(claim));
        }
    }

    private long createTodayDose(Account patient) throws Exception {
        long medicationId = createMedication(patient);
        createSchedule(patient, medicationId, "ONCE_DAILY");
        JsonNode doses = today(patient);
        for (JsonNode dose : doses) {
            if (dose.path("status").asText().equals("PENDING")) {
                return dose.path("id").asLong();
            }
        }
        throw new AssertionError("Expected a newly materialized pending dose");
    }

    private void markMissed(Account patient, long doseId) throws Exception {
        mockMvc.perform(post("/api/v1/adherence/{id}/missed", doseId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk());
    }

    private long createMedication(Account patient) throws Exception {
        String body = "{\"name\":\"Test Medication\",\"dosage\":\"1 tablet\",\"frequency\":\"daily\",\"startDate\":\"%s\"}"
                .formatted(LocalDate.now());
        MvcResult result = mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createSchedule(Account patient, long medicationId, String frequency) throws Exception {
        String body = "{\"frequency\":\"%s\",\"timeOfDay\":\"08:00\",\"startDate\":\"%s\",\"endDate\":null}"
                .formatted(frequency, LocalDate.now());
        MvcResult result = mockMvc.perform(post("/api/v1/medications/{id}/schedules", medicationId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private JsonNode today(Account patient) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/adherence/today")
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private long createRelationship(Account patient, Account related, String type) throws Exception {
        String body = "{\"relatedUserEmail\":\"%s\",\"relationshipType\":\"%s\"}"
                .formatted(related.email(), type);
        MvcResult result = mockMvc.perform(post("/api/v1/relationships")
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long acceptRelationship(Account patient, Account related, String type) throws Exception {
        long id = createRelationship(patient, related, type);
        mockMvc.perform(post("/api/v1/relationships/{id}/accept", id)
                        .header("Authorization", bearer(related.token())))
                .andExpect(status().isOk());
        return id;
    }

    private Account register(String role) throws Exception {
        String email = "phase8." + role.toLowerCase() + "." + UUID.randomUUID() + "@example.com";
        String body = "{\"fullName\":\"Phase Eight User\",\"email\":\"%s\",\"password\":\"SmartMed@123\",\"role\":\"%s\"}"
                .formatted(email, role);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new Account(data.path("accessToken").asText(), email);
    }

    private JsonNode notificationItems(String token, int page, int size) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/notifications").param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("items");
    }

    private long unreadCount(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("count").asLong();
    }

    private static long countType(JsonNode items, String type) {
        long count = 0;
        for (JsonNode item : items) {
            if (item.path("type").asText().equals(type)) count++;
        }
        return count;
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Account(String token, String email) { }
}
