package com.smartmed.service;

import com.smartmed.dto.response.MarkAllReadResponse;
import com.smartmed.dto.response.NotificationListResponse;
import com.smartmed.dto.response.NotificationResponse;
import com.smartmed.entity.Notification;
import com.smartmed.entity.NotificationRelatedEntityType;
import com.smartmed.entity.NotificationType;
import com.smartmed.entity.User;
import com.smartmed.exception.InvalidNotificationPageException;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void createIfAbsent(User recipient, NotificationType type, String title, String message,
                               NotificationRelatedEntityType relatedEntityType, Long relatedEntityId,
                               String deduplicationKey) {
        if (notificationRepository.existsByRecipientIdAndTypeAndDeduplicationKey(
                recipient.getId(), type, deduplicationKey)) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRelatedEntityType(relatedEntityType);
        notification.setRelatedEntityId(relatedEntityId);
        notification.setDeduplicationKey(deduplicationKey);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long recipientId, boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidNotificationPageException(
                    "page must be non-negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        PageRequest pageable = PageRequest.of(page, size);
        Page<Notification> result = unreadOnly
                ? notificationRepository.findAllByRecipientIdAndReadFalseOrderByCreatedAtDescIdDesc(
                        recipientId, pageable)
                : notificationRepository.findAllByRecipientIdOrderByCreatedAtDescIdDesc(recipientId, pageable);
        return new NotificationListResponse(result.getContent().stream()
                .map(NotificationResponse::from).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId, Long recipientId) {
        return updateReadState(notificationId, recipientId, true);
    }

    @Transactional
    public NotificationResponse markUnread(Long notificationId, Long recipientId) {
        return updateReadState(notificationId, recipientId, false);
    }

    @Transactional
    public MarkAllReadResponse markAllRead(Long recipientId) {
        return new MarkAllReadResponse(notificationRepository.markAllReadByRecipientId(recipientId));
    }

    private NotificationResponse updateReadState(Long notificationId, Long recipientId, boolean read) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.isRead() != read) {
            notification.setRead(read);
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }
}
