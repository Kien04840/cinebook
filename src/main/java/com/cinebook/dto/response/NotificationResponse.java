package com.cinebook.dto.response;

import com.cinebook.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private String id;
    private String title;
    private String message;
    private NotificationType type;
    private String bookingId;
    private String bookingCode;
    private boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}

