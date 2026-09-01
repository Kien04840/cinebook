package com.cinebook.dto.response;

import com.cinebook.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketVerifyResponse {

    private String ticketId;
    private String qrCode;
    private BigDecimal ticketPrice;
    private TicketStatus ticketStatus;

    private String bookingId;
    private String bookingCode;
    private String customerName;
    private String customerEmail;

    private String movieTitle;
    private String moviePosterUrl;
    private String cinemaName;
    private String auditoriumName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String rowLabel;
    private Integer seatNumber;
    private String seatCode;
    private String seatTypeName;

    private boolean checkInEligible;
    private String ineligibleReason;
}

