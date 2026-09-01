package com.cinebook.dto.response;

import com.cinebook.enums.BookingStatus;
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
public class BookingSummaryResponse {

    private String id;
    private String bookingCode;
    private BookingStatus bookingStatus;
    private BigDecimal totalAmount;
    private LocalDateTime holdExpiresAt;
    private LocalDateTime createdAt;
    private Integer seatCount;
    private ShowtimeSummaryResponse showtime;
    private UserSummaryResponse user;
}

