package com.cinebook.mapper;

import com.cinebook.dto.response.NotificationResponse;
import com.cinebook.entity.Notification;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class NotificationMapper {

    public NotificationResponse toNotificationResponse(Notification notification) {
        if (notification == null) {
            return null;
        }

        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .bookingId(notification.getBookingId())
                .bookingCode(notification.getBookingCode())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }

    public List<NotificationResponse> toNotificationResponseList(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyList();
        }

        return notifications.stream()
                .map(this::toNotificationResponse)
                .toList();
    }
}

