package com.cinebook.dto.response;

import com.cinebook.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    private String id;
    private String seatId;
    private String seatCode;
    private BigDecimal ticketPrice;
    private TicketStatus ticketStatus;
    private String qrCode;
    private LocalDateTime createdAt;
}

