package com.smartmed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.entity.Medication;
import com.smartmed.entity.NotificationType;
import com.smartmed.repository.CareRelationshipRepository;
import com.smartmed.repository.DoseRecordRepository;
import com.smartmed.repository.MedicationRepository;
import com.smartmed.repository.MedicationScheduleRepository;
import com.smartmed.repository.NotificationRepository;
import com.smartmed.repository.UserRepository;
import com.smartmed.service.NotificationService;
import com.smartmed.service.interaction.DrugInteractionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Phase 4 low-stock alert lifecycle.
 * Verifies notification creation, deduplication, re-arming, and
 * correct behavior across threshold changes and inventory operations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LowStockAlertIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CareRelationshipRepository relationshipRepository;
    @Autowired private DoseRecordRepository doseRepository;
    @Autowired private MedicationScheduleRepository scheduleRepository;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private UserRepository userRepository;
    @MockBean private DrugInteractionProvider interactionProvider;
    @SpyBean private NotificationService notificationService;

    private static final String TODAY = LocalDate.now().toString();

    @BeforeEach
    void cleanDatabase() {
        notificationRepository.deleteAll();
        relationshipRepository.deleteAll();
        doseRepository.deleteAll();
        scheduleRepository.deleteAll();
        medicationRepository.deleteAll();
        userRepository.deleteAll();
        reset(interactionProvider, notificationService);
        when(interactionProvider.getProviderId()).thenReturn("test-provider");
        when(interactionProvider.checkInteractions(anyList())).thenReturn(List.of());
    }

    // ---------------------------------------------------------------
    // 1. Creation below a positive threshold creates exactly one alert
    // ---------------------------------------------------------------
    @Test
    void creationBelowPositiveThresholdCreatesOneAlert() throws Exception {
        Account patient = register();
        createMedicationWithThreshold(patient, "Aspirin", 5L, "tablets", 10L);

        assertThat(lowStockNotificationCount()).isEqualTo(1);
    }

    @Test
    void creationAtExactThresholdCreatesOneAlert() throws Exception {
        Account patient = register();
        createMedicationWithThreshold(patient, "Aspirin", 10L, "tablets", 10L);

        assertThat(lowStockNotificationCount()).isEqualTo(1);
    }

    @Test
    void creationAboveThresholdCreatesNoAlert() throws Exception {
        Account patient = register();
        createMedicationWithThreshold(patient, "Aspirin", 20L, "tablets", 10L);

        assertThat(lowStockNotificationCount()).isZero();
    }

    // ---------------------------------------------------------------
    // 2. NULL and zero thresholds disable alerts
    // ---------------------------------------------------------------
    @Test
    void nullThresholdCreatesNoAlert() throws Exception {
        Account patient = register();
        createMedicationWithThreshold(patient, "Aspirin", 0L, "tablets", null);

        assertThat(lowStockNotificationCount()).isZero();
    }

    @Test
    void zeroThresholdCreatesNoAlert() throws Exception {
        Account patient = register();
        createMedicationWithThreshold(patient, "Aspirin", 0L, "tablets", 0L);

        assertThat(lowStockNotificationCount()).isZero();
    }

    // ---------------------------------------------------------------
    // 3. Negative thresholds are rejected
    // ---------------------------------------------------------------
    @Test
    void negativeThresholdIsRejectedOnCreate() throws Exception {
        Account patient = register();
        String body = medicationJson("Aspirin", 10L, "tablets", -5L);
        mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(lowStockNotificationCount()).isZero();
        assertThat(medicationRepository.findAll()).isEmpty();
    }

    @Test
    void negativeThresholdIsRejectedOnUpdate() throws Exception {
        Account patient = register();
        long medId = createMedicationWithThreshold(patient, "Aspirin", 20L, "tablets", 10L);
        String body = medicationJson("Aspirin", 20L, "tablets", -1L);
        mockMvc.perform(put("/api/v1/medications/{id}", medId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // Verify original medication state is preserved
        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getLowStockThreshold()).isEqualTo(10L);
    }

    // ---------------------------------------------------------------
    // 4. Crossing the threshold via dose deduction creates one alert
    // ---------------------------------------------------------------
    @Test
    void doseDeductionCrossingThresholdCreatesOneAlert() throws Exception {
        Account patient = register();
        // quantity=12, threshold=10 → above threshold, no alert on creation
        long medId = createMedicationWithThreshold(patient, "Metformin", 12L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isZero();

        // Create schedule with verified doseSize=3, take a dose → quantity becomes 9, below threshold
        createVerifiedSchedule(patient, medId, 3L);
        long doseId = getTodayPendingDoseId(patient);
        markTaken(patient, doseId);

        assertThat(lowStockNotificationCount()).isEqualTo(1);
        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getQuantity()).isEqualTo(9L);
        assertThat(med.getLowStockAlertActive()).isTrue();
    }

    // ---------------------------------------------------------------
    // 5. Repeated deductions while low do not duplicate alerts
    // ---------------------------------------------------------------
    @Test
    void repeatedDeductionsWhileLowDoNotDuplicateAlerts() throws Exception {
        Account patient = register();
        // Start below threshold
        long medId = createMedicationWithThreshold(patient, "Ibuprofen", 8L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        // Take two more doses — still below threshold, no new alerts
        createVerifiedSchedule(patient, medId, 1L);
        long dose1 = getTodayPendingDoseId(patient);
        markTaken(patient, dose1);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        // Create another schedule with a different time to get a new dose
        createVerifiedScheduleAtTime(patient, medId, 1L, "14:00");
        // Re-materialize
        long dose2 = getAnyPendingDoseId(patient);
        markTaken(patient, dose2);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getQuantity()).isEqualTo(6L);
    }

    // ---------------------------------------------------------------
    // 6. Partial refill while low does not duplicate alerts
    // ---------------------------------------------------------------
    @Test
    void partialRefillDoesNotDuplicateAlert() throws Exception {
        Account patient = register();
        // Start below threshold: quantity=3, threshold=10
        long medId = createMedicationWithThreshold(patient, "Vitamin D", 3L, "capsules", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        // Partial refill: 3 + 5 = 8, still below threshold
        refill(patient, medId, 5L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getQuantity()).isEqualTo(8L);
        assertThat(med.getLowStockAlertActive()).isTrue();
    }

    // ---------------------------------------------------------------
    // 7. Refill above threshold re-arms alerts
    // ---------------------------------------------------------------
    @Test
    void refillAboveThresholdRearmsAlerts() throws Exception {
        Account patient = register();
        long medId = createMedicationWithThreshold(patient, "Iron", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        // Refill to above threshold: 5 + 20 = 25
        refill(patient, medId, 20L);
        assertThat(lowStockNotificationCount()).isEqualTo(1); // still the same notification

        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getQuantity()).isEqualTo(25L);
        assertThat(med.getLowStockAlertActive()).isFalse();
        assertThat(med.getLowStockEpisode()).isEqualTo(1L); // episode incremented
    }

    // ---------------------------------------------------------------
    // 8. Subsequent low-stock episode creates a second alert
    // ---------------------------------------------------------------
    @Test
    void subsequentLowStockEpisodeCreatesSecondAlert() throws Exception {
        Account patient = register();
        // Episode 0: create below threshold
        long medId = createMedicationWithThreshold(patient, "Calcium", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        // Re-arm: refill above threshold
        refill(patient, medId, 20L);
        Medication after = medicationRepository.findById(medId).orElseThrow();
        assertThat(after.getLowStockAlertActive()).isFalse();
        assertThat(after.getLowStockEpisode()).isEqualTo(1L);

        // Episode 1: deduction puts us below threshold again
        createVerifiedSchedule(patient, medId, 20L);
        long doseId = getTodayPendingDoseId(patient);
        markTaken(patient, doseId);

        assertThat(lowStockNotificationCount()).isEqualTo(2);
        Medication finalMed = medicationRepository.findById(medId).orElseThrow();
        assertThat(finalMed.getQuantity()).isEqualTo(5L);
        assertThat(finalMed.getLowStockAlertActive()).isTrue();
        assertThat(finalMed.getLowStockEpisode()).isEqualTo(1L); // not yet incremented again
    }

    // ---------------------------------------------------------------
    // 9. Threshold changes via medication update
    // ---------------------------------------------------------------
    @Test
    void enablingThresholdOnExistingLowStockMedicationCreatesAlert() throws Exception {
        Account patient = register();
        // Create with no threshold
        long medId = createMedicationWithThreshold(patient, "Zinc", 3L, "tablets", null);
        assertThat(lowStockNotificationCount()).isZero();

        // Update: enable threshold=10 while quantity=3
        updateMedication(patient, medId, "Zinc", 3L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);
    }

    @Test
    void disablingThresholdClearsAlertState() throws Exception {
        Account patient = register();
        long medId = createMedicationWithThreshold(patient, "Magnesium", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        Medication medBefore = medicationRepository.findById(medId).orElseThrow();
        assertThat(medBefore.getLowStockAlertActive()).isTrue();

        // Disable threshold with an explicit JSON null; omitting the field preserves it.
        updateMedicationWithExplicitNullThreshold(patient, medId, "Magnesium", 5L, "tablets");

        Medication medAfter = medicationRepository.findById(medId).orElseThrow();
        assertThat(medAfter.getLowStockAlertActive()).isFalse();
        assertThat(medAfter.getLowStockEpisode()).isEqualTo(1L);
    }

    @Test
    void raisingThresholdAboveQuantityCreatesAlert() throws Exception {
        Account patient = register();
        // quantity=15, threshold=10 → above threshold, no alert
        long medId = createMedicationWithThreshold(patient, "B12", 15L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isZero();

        // Raise threshold to 20 → now 15 <= 20, should trigger alert
        updateMedication(patient, medId, "B12", 15L, "tablets", 20L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);
    }

    @Test
    void loweringThresholdBelowQuantityRearmsWithoutNewAlert() throws Exception {
        Account patient = register();
        // quantity=5, threshold=10 → below threshold → alert
        long medId = createMedicationWithThreshold(patient, "Folate", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        // Lower threshold to 3 → now 5 > 3, re-arms
        updateMedication(patient, medId, "Folate", 5L, "tablets", 3L);
        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getLowStockAlertActive()).isFalse();
        assertThat(med.getLowStockEpisode()).isEqualTo(1L);
        // No new alert created
        assertThat(lowStockNotificationCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // 10. Failed inventory operations create no persistent alerts
    // ---------------------------------------------------------------
    @Test
    void failedRefillOverflowCreatesNoPersistentAlert() throws Exception {
        Account patient = register();
        // Start above threshold with very high quantity
        long medId = createMedicationWithThreshold(patient, "Overflow Med", 100L, "units", 10L);
        assertThat(lowStockNotificationCount()).isZero();

        // Manually set quantity close to Long.MAX_VALUE
        Medication med = medicationRepository.findById(medId).orElseThrow();
        med.setQuantity(Long.MAX_VALUE - 5L);
        medicationRepository.saveAndFlush(med);

        // Attempt overflow refill
        mockMvc.perform(post("/api/v1/medications/{id}/refill", medId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_OVERFLOW"));

        // No new alerts created; quantity unchanged
        assertThat(lowStockNotificationCount()).isZero();
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity())
                .isEqualTo(Long.MAX_VALUE - 5L);
    }

    // ---------------------------------------------------------------
    // 11. Insufficient inventory on dose-taken creates no alert
    // ---------------------------------------------------------------
    @Test
    void insufficientInventoryOnDoseTakenCreatesNoAlert() throws Exception {
        Account patient = register();
        // quantity=2, threshold=10, doseSize=5 → can't take dose
        long medId = createMedicationWithThreshold(patient, "Scarce Med", 2L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1); // creation-time alert

        createVerifiedSchedule(patient, medId, 5L);
        long doseId = getTodayPendingDoseId(patient);

        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_INVENTORY"));

        // Still exactly 1 alert (from creation), quantity unchanged
        assertThat(lowStockNotificationCount()).isEqualTo(1);
        assertThat(medicationRepository.findById(medId).orElseThrow().getQuantity()).isEqualTo(2L);
    }

    // ---------------------------------------------------------------
    // 12. Medication response includes lowStock boolean flag
    // ---------------------------------------------------------------
    @Test
    void medicationResponseIncludesLowStockFlag() throws Exception {
        Account patient = register();
        long medId = createMedicationWithThreshold(patient, "Flagged Med", 5L, "tablets", 10L);

        MvcResult result = mockMvc.perform(get("/api/v1/medications/{id}", medId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStock").value(true))
                .andExpect(jsonPath("$.data.lowStockThreshold").value(10))
                .andExpect(jsonPath("$.data.quantity").value(5))
                .andReturn();

        // Refill above threshold and verify flag clears
        refill(patient, medId, 20L);
        mockMvc.perform(get("/api/v1/medications/{id}", medId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStock").value(false))
                .andExpect(jsonPath("$.data.quantity").value(25));
    }

    // ---------------------------------------------------------------
    // 13. Alert notification message contains correct content
    // ---------------------------------------------------------------
    @Test
    void lowStockNotificationContainsMedicationNameAndQuantity() throws Exception {
        Account patient = register();
        createMedicationWithThreshold(patient, "Lisinopril", 3L, "tablets", 10L);

        var notifications = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.LOW_STOCK)
                .toList();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getMessage()).contains("Lisinopril");
        assertThat(notifications.get(0).getMessage()).contains("3 tablets");
        assertThat(notifications.get(0).getTitle()).isEqualTo("Low medication stock");
    }

    // ---------------------------------------------------------------
    // 14. Threshold update semantics: omitted vs explicit null vs zero
    // ---------------------------------------------------------------

    @Test
    void omittedThresholdOnUpdatePreservesExistingThreshold() throws Exception {
        Account patient = register();
        long medId = createMedicationWithThreshold(patient, "OmitTest", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        // Send update with lowStockThreshold omitted from JSON
        String body = "{\"name\":\"OmitTest\",\"dosage\":\"1 tablet\",\"frequency\":\"Once daily\","
                + "\"startDate\":\"" + TODAY + "\",\"quantity\":5,\"unit\":\"tablets\"}";
        mockMvc.perform(put("/api/v1/medications/{id}", medId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getLowStockThreshold()).isEqualTo(10L);
        assertThat(med.getLowStockAlertActive()).isTrue();
        assertThat(med.getLowStockEpisode()).isZero();
    }

    @Test
    void explicitNullThresholdOnUpdateDisablesAlerts() throws Exception {
        Account patient = register();
        long medId = createMedicationWithThreshold(patient, "NullTest", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        // Send update with explicit "lowStockThreshold": null
        String body = "{\"name\":\"NullTest\",\"dosage\":\"1 tablet\",\"frequency\":\"Once daily\","
                + "\"startDate\":\"" + TODAY + "\",\"quantity\":5,\"unit\":\"tablets\","
                + "\"lowStockThreshold\":null}";
        mockMvc.perform(put("/api/v1/medications/{id}", medId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getLowStockThreshold()).isNull();
        assertThat(med.getLowStockAlertActive()).isFalse();
        assertThat(med.getLowStockEpisode()).isEqualTo(1L);
    }

    @Test
    void zeroThresholdOnUpdateDisablesAlerts() throws Exception {
        Account patient = register();
        long medId = createMedicationWithThreshold(patient, "ZeroTest", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        updateMedication(patient, medId, "ZeroTest", 5L, "tablets", 0L);

        Medication med = medicationRepository.findById(medId).orElseThrow();
        assertThat(med.getLowStockThreshold()).isEqualTo(0L);
        assertThat(med.getLowStockAlertActive()).isFalse();
        assertThat(med.getLowStockEpisode()).isEqualTo(1L);
    }

    @Test
    void reEnablingThresholdReevaluatesCurrentInventory() throws Exception {
        Account patient = register();
        // Create with threshold, then disable, then re-enable
        long medId = createMedicationWithThreshold(patient, "ReEnable", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1); // episode 0 alert

        // Disable
        updateMedication(patient, medId, "ReEnable", 5L, "tablets", 0L);
        Medication disabled = medicationRepository.findById(medId).orElseThrow();
        assertThat(disabled.getLowStockAlertActive()).isFalse();
        assertThat(disabled.getLowStockEpisode()).isEqualTo(1L);

        // Re-enable with quantity still below threshold → new episode alert
        updateMedication(patient, medId, "ReEnable", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(2); // episode 1 alert
        Medication reenabled = medicationRepository.findById(medId).orElseThrow();
        assertThat(reenabled.getLowStockAlertActive()).isTrue();
        assertThat(reenabled.getLowStockEpisode()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // 15. Notification visibility is restricted to the owning patient
    // ---------------------------------------------------------------
    @Test
    void lowStockNotificationIsOnlyVisibleToOwningPatient() throws Exception {
        Account owner = register();
        Account other = register();
        createMedicationWithThreshold(owner, "OwnerMed", 5L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        // Owner sees notification
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("LOW_STOCK"));

        // Other patient sees nothing
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // Unread count is correct per patient
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        long notificationId = notificationRepository.findAll().get(0).getId();
        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound());
        assertThat(notificationRepository.findById(notificationId).orElseThrow().isRead()).isFalse();
    }

    // ---------------------------------------------------------------
    // 16. Transaction rollback: failed dose-taken leaves no side effects
    // ---------------------------------------------------------------
    @Test
    void failedDoseTakenDoesNotLeaveOrphanedAlertState() throws Exception {
        Account patient = register();
        // Create above threshold so no creation alert
        long medId = createMedicationWithThreshold(patient, "TxRollback", 20L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isZero();

        // Create schedule with doseSize that would bring below threshold if taken
        createVerifiedSchedule(patient, medId, 15L);
        long doseId = getTodayPendingDoseId(patient);

        // Manually set quantity to exactly the dose size to create the edge case
        // where deduction succeeds but imagine a constraint fails
        Medication med = medicationRepository.findById(medId).orElseThrow();
        long originalQuantity = med.getQuantity();

        // Attempt dose-taken with insufficient stock
        med.setQuantity(3L); // Below doseSize of 15
        medicationRepository.saveAndFlush(med);

        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_INVENTORY"));

        // Verify: no new low-stock notifications were persisted
        // (the creation-time check at quantity=20 threshold=10 would have been none)
        assertThat(lowStockNotificationCount()).isZero();
        // Quantity is unchanged at the value we set (3), not further modified
        Medication afterFail = medicationRepository.findById(medId).orElseThrow();
        assertThat(afterFail.getQuantity()).isEqualTo(3L);
        assertThat(afterFail.getLowStockAlertActive()).isFalse();
    }

    @Test
    void failedDoseTakenPreservesExistingAlertState() throws Exception {
        Account patient = register();
        // Create below threshold → alert active
        long medId = createMedicationWithThreshold(patient, "PreserveState", 8L, "tablets", 10L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);

        Medication before = medicationRepository.findById(medId).orElseThrow();
        assertThat(before.getLowStockAlertActive()).isTrue();
        assertThat(before.getLowStockEpisode()).isEqualTo(0L);

        // Set quantity below dose size to force failure
        before.setQuantity(2L);
        medicationRepository.saveAndFlush(before);

        createVerifiedSchedule(patient, medId, 5L);
        long doseId = getTodayPendingDoseId(patient);

        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isConflict());

        // Alert state unchanged: still active, episode still 0, still 1 notification
        Medication after = medicationRepository.findById(medId).orElseThrow();
        assertThat(after.getLowStockAlertActive()).isTrue();
        assertThat(after.getLowStockEpisode()).isEqualTo(0L);
        assertThat(lowStockNotificationCount()).isEqualTo(1);
    }

    @Test
    void notificationPersistenceFailureRollsBackLowStockMedicationCreation() throws Exception {
        Account patient = register();
        doThrow(new IllegalStateException("notification persistence failed"))
                .when(notificationService).createIfAbsent(
                        any(), eq(NotificationType.LOW_STOCK), any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(medicationJson("RollbackMed", 5L, "tablets", 10L)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));

        assertThat(medicationRepository.findAll()).isEmpty();
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private long createMedicationWithThreshold(Account patient, String name,
                                                Long quantity, String unit,
                                                Long threshold) throws Exception {
        String body = medicationJson(name, quantity, unit, threshold);
        MvcResult result = mockMvc.perform(post("/api/v1/medications")
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private void updateMedication(Account patient, long medId, String name,
                                   Long quantity, String unit,
                                   Long threshold) throws Exception {
        String body = medicationJson(name, quantity, unit, threshold);
        mockMvc.perform(put("/api/v1/medications/{id}", medId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void updateMedicationWithExplicitNullThreshold(Account patient, long medId, String name,
                                                            Long quantity, String unit) throws Exception {
        String body = "{\"name\":\"%s\",\"dosage\":\"1 tablet\",\"frequency\":\"Once daily\","
                .formatted(name)
                + "\"startDate\":\"%s\",\"quantity\":%d,\"unit\":\"%s\",\"lowStockThreshold\":null}"
                .formatted(TODAY, quantity, unit);
        mockMvc.perform(put("/api/v1/medications/{id}", medId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void refill(Account patient, long medId, long amount) throws Exception {
        mockMvc.perform(post("/api/v1/medications/{id}/refill", medId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private void createVerifiedSchedule(Account patient, long medId, long doseSize)
            throws Exception {
        createVerifiedScheduleAtTime(patient, medId, doseSize, "08:00");
    }

    private void createVerifiedScheduleAtTime(Account patient, long medId,
                                               long doseSize, String time)
            throws Exception {
        String body = "{\"frequency\":\"ONCE_DAILY\",\"timeOfDay\":\"%s\",\"startDate\":\"%s\",\"doseSize\":%d}"
                .formatted(time, TODAY, doseSize);
        mockMvc.perform(post("/api/v1/medications/{id}/schedules", medId)
                        .header("Authorization", bearer(patient.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private long getTodayPendingDoseId(Account patient) throws Exception {
        JsonNode doses = today(patient);
        for (JsonNode dose : doses) {
            if ("PENDING".equals(dose.path("status").asText())) {
                return dose.path("id").asLong();
            }
        }
        throw new AssertionError("No pending dose found for today");
    }

    private long getAnyPendingDoseId(Account patient) throws Exception {
        JsonNode doses = today(patient);
        for (JsonNode dose : doses) {
            if ("PENDING".equals(dose.path("status").asText())) {
                return dose.path("id").asLong();
            }
        }
        throw new AssertionError("No pending dose found for today");
    }

    private void markTaken(Account patient, long doseId) throws Exception {
        mockMvc.perform(post("/api/v1/adherence/{id}/taken", doseId)
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk());
    }

    private JsonNode today(Account patient) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/adherence/today")
                        .header("Authorization", bearer(patient.token())))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private long lowStockNotificationCount() {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.LOW_STOCK)
                .count();
    }

    private String medicationJson(String name, Long quantity, String unit, Long threshold) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"%s\",\"dosage\":\"1 tablet\",\"frequency\":\"Once daily\",".formatted(name));
        sb.append("\"startDate\":\"%s\"".formatted(TODAY));
        if (quantity != null) sb.append(",\"quantity\":%d".formatted(quantity));
        if (unit != null) sb.append(",\"unit\":\"%s\"".formatted(unit));
        if (threshold != null) sb.append(",\"lowStockThreshold\":%d".formatted(threshold));
        sb.append("}");
        return sb.toString();
    }

    private Account register() throws Exception {
        String email = "lowstock." + UUID.randomUUID() + "@example.com";
        String body = "{\"fullName\":\"LowStock Test User\",\"email\":\"%s\",\"password\":\"SmartMed@123\",\"role\":\"PATIENT\"}"
                .formatted(email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new Account(data.path("accessToken").asText(), email);
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Account(String token, String email) { }
}
