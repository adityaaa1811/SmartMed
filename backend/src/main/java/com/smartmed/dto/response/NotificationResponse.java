package com.smartmed.dto.response;

import com.smartmed.entity.Notification;
import com.smartmed.entity.NotificationRelatedEntityType;
import com.smartmed.entity.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        NotificationRelatedEntityType relatedEntityType,
        Long relatedEntityId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(),
                notification.getTitle(), notification.getMessage(), notification.getRelatedEntityType(),
                notification.getRelatedEntityId(), notification.isRead(), notification.getCreatedAt());
    }
}
