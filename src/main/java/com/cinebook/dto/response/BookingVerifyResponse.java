package com.cinebook.dto.response;

import com.cinebook.enums.BookingStatus;
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
public class BookingVerifyResponse {
    private String bookingId;
    private String bookingCode;
    private String checkInCode;
    private BookingStatus bookingStatus;

    private String customerName;
    private String customerEmail;
    private String customerPhone;

    private String movieTitle;
    private String moviePosterUrl;
    private String cinemaName;
    private String auditoriumName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private List<BookingTicketItemResponse> tickets;
    private int totalTickets;
    private int validTickets;
    private int usedTickets;

    private boolean checkInEligible;
    private String ineligibleReason;
}

