package com.cinebook.dto.response;

import com.cinebook.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingTicketItemResponse {
    private String ticketId;
    private String seatCode;
    private String rowLabel;
    private Integer seatNumber;
    private String seatTypeName;
    private BigDecimal ticketPrice;
    private TicketStatus ticketStatus;
}

