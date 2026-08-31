package com.cinebook.dto.response;

import com.cinebook.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDetailResponse {

    private String id;
    private String bookingCode;
    private BookingStatus bookingStatus;
    private BigDecimal totalAmount;
    private BigDecimal grossAmount;
    private BigDecimal discountAmount;
    private LocalDateTime holdExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime cancelledAt;
    private String cancelledReason;
    private ShowtimeDetailResponse showtime;
    private List<BookingSeatResponse> seats;
    private List<TicketResponse> tickets;
    private List<PaymentSummaryResponse> payments;
    private BookingPromotionResponse promotion;
}


