package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCheckInResponse {
    private String bookingId;
    private String bookingCode;
    private String checkInCode;
    private String result;
    private LocalDateTime checkedInAt;
    private String message;

    private String movieTitle;
    private String cinemaName;
    private String auditoriumName;
    private LocalDateTime startTime;

    private List<BookingTicketItemResponse> tickets;
    private int totalTickets;
    private int checkedInCount;
    private int alreadyUsedCount;
}

