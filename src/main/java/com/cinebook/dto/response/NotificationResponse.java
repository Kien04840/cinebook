package com.cinebook.dto.response;

import com.cinebook.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isRead")
    private boolean isRead;

    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    @JsonProperty("isRead")
    public boolean isRead() {
        return isRead;
    }

    @JsonProperty("isRead")
    public void setRead(boolean isRead) {
        this.isRead = isRead;
    }

    public String getReferenceType() {
        return "BOOKING";
    }

    public String getReferenceId() {
        return this.bookingId;
    }
}

