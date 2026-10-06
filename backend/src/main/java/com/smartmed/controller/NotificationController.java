package com.smartmed.controller;

import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.MarkAllReadResponse;
import com.smartmed.dto.response.NotificationCountResponse;
import com.smartmed.dto.response.NotificationListResponse;
import com.smartmed.dto.response.NotificationResponse;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(notificationService.list(principal.getId(), unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationCountResponse> unreadCount(
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(new NotificationCountResponse(notificationService.unreadCount(principal.getId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(notificationService.markRead(id, principal.getId()));
    }

    @PostMapping("/{id}/unread")
    public ApiResponse<NotificationResponse> markUnread(
            @PathVariable Long id,
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(notificationService.markUnread(id, principal.getId()));
    }

    @PostMapping("/read-all")
    public ApiResponse<MarkAllReadResponse> markAllRead(
            @AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(notificationService.markAllRead(principal.getId()));
    }
}
