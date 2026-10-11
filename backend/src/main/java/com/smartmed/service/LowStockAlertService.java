package com.smartmed.service;

import com.smartmed.entity.Medication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LowStockAlertService {

    private final NotificationEventService notificationEventService;

    public LowStockAlertService(NotificationEventService notificationEventService) {
        this.notificationEventService = notificationEventService;
    }

    @Transactional
    public void evaluateAndNotify(Medication medication) {
        Long threshold = medication.getLowStockThreshold();
        long quantity = medication.getQuantity() != null ? medication.getQuantity() : 0L;

        // Threshold disabled: null or <= 0
        if (threshold == null || threshold <= 0) {
            if (Boolean.TRUE.equals(medication.getLowStockAlertActive())) {
                medication.setLowStockAlertActive(false);
                medication.setLowStockEpisode(medication.getLowStockEpisode() + 1);
            }
            return;
        }

        // Threshold enabled: positive
        if (quantity <= threshold) {
            if (!Boolean.TRUE.equals(medication.getLowStockAlertActive())) {
                medication.setLowStockAlertActive(true);
                notificationEventService.lowStock(medication);
            }
            return;
        }

        // Quantity is above threshold: if previously low, re-arm for next cycle
        if (Boolean.TRUE.equals(medication.getLowStockAlertActive())) {
            medication.setLowStockAlertActive(false);
            medication.setLowStockEpisode(medication.getLowStockEpisode() + 1);
        }
    }
}
