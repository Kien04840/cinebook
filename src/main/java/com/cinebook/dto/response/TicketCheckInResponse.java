package com.cinebook.dto.response;

import com.cinebook.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCheckInResponse {

    private String ticketId;
    private String qrCode;
    private TicketStatus ticketStatus;
    private LocalDateTime checkedInAt;
    private String message;

    private String bookingCode;
    private String seatCode;
    private String movieTitle;
    private String auditoriumName;
    private LocalDateTime startTime;
}

