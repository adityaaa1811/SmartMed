package com.smartmed.dto.response;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public NotificationListResponse {
        items = List.copyOf(items);
    }
}
