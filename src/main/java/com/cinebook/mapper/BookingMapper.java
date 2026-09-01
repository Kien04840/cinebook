package com.cinebook.mapper;

import com.cinebook.dto.response.*;
import com.cinebook.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class BookingMapper {

    private final ShowtimeMapper showtimeMapper;
    private final RefundMapper refundMapper;

    public BookingMapper(ShowtimeMapper showtimeMapper) {
        this.showtimeMapper = showtimeMapper;
        this.refundMapper = null;
    }

    @Autowired
    public BookingMapper(ShowtimeMapper showtimeMapper, RefundMapper refundMapper) {
        this.showtimeMapper = showtimeMapper;
        this.refundMapper = refundMapper;
    }


    public BookingSummaryResponse toBookingSummaryResponse(Booking booking) {
        if (booking == null) {
            return null;
        }

        int seatCount = 0;
        if (booking.getTickets() != null && !booking.getTickets().isEmpty()) {
            seatCount = booking.getTickets().size();
        } else if (booking.getSeatHolds() != null && !booking.getSeatHolds().isEmpty()) {
            seatCount = booking.getSeatHolds().size();
        }

        ShowtimeSummaryResponse showtimeSummary = booking.getShowtime() != null
                ? showtimeMapper.toShowtimeSummaryResponse(booking.getShowtime())
                : null;

        UserSummaryResponse userSummary = booking.getUser() != null
                ? UserSummaryResponse.builder()
                .id(booking.getUser().getId())
                .email(booking.getUser().getEmail())
                .fullName(booking.getUser().getFullName())
                .phone(booking.getUser().getPhone())
                .build()
                : null;

        return BookingSummaryResponse.builder()
                .id(booking.getId())
                .bookingCode(booking.getBookingCode())
                .bookingStatus(booking.getBookingStatus())
                .totalAmount(booking.getTotalAmount())
                .holdExpiresAt(booking.getHoldExpiresAt())
                .createdAt(booking.getCreatedAt())
                .seatCount(seatCount)
                .showtime(showtimeSummary)
                .user(userSummary)
                .build();
    }

    public BookingDetailResponse toBookingDetailResponse(
            Booking booking,
            List<BookingSeatResponse> seats,
            List<TicketResponse> tickets,
            List<PaymentSummaryResponse> payments
    ) {
        return toBookingDetailResponse(booking, seats, tickets, payments, null);
    }

    public BookingDetailResponse toBookingDetailResponse(
            Booking booking,
            List<BookingSeatResponse> seats,
            List<TicketResponse> tickets,
            List<PaymentSummaryResponse> payments,
            BookingPromotionResponse promotion
    ) {
        if (booking == null) {
            return null;
        }

        BigDecimal grossAmount = BigDecimal.ZERO;
        if (seats != null) {
            for (BookingSeatResponse seat : seats) {
                if (seat.getPrice() != null) {
                    grossAmount = grossAmount.add(seat.getPrice());
                }
            }
        }

        BigDecimal discountAmount = (promotion != null && promotion.getDiscountAmount() != null)
                ? promotion.getDiscountAmount()
                : BigDecimal.ZERO;

        ShowtimeDetailResponse showtimeDetail = booking.getShowtime() != null
                ? showtimeMapper.toShowtimeDetailResponse(booking.getShowtime())
                : null;

        UserSummaryResponse userSummary = booking.getUser() != null
                ? UserSummaryResponse.builder()
                .id(booking.getUser().getId())
                .email(booking.getUser().getEmail())
                .fullName(booking.getUser().getFullName())
                .phone(booking.getUser().getPhone())
                .build()
                : null;

        return BookingDetailResponse.builder()
                .id(booking.getId())
                .bookingCode(booking.getBookingCode())
                .bookingStatus(booking.getBookingStatus())
                .totalAmount(booking.getTotalAmount())
                .grossAmount(grossAmount)
                .discountAmount(discountAmount)
                .holdExpiresAt(booking.getHoldExpiresAt())
                .createdAt(booking.getCreatedAt())
                .cancelledAt(booking.getCancelledAt())
                .cancelledReason(booking.getCancelledReason())
                .showtime(showtimeDetail)
                .seats(seats != null ? seats : Collections.emptyList())
                .tickets(tickets != null ? tickets : Collections.emptyList())
                .payments(payments != null ? payments : Collections.emptyList())
                .promotion(promotion)
                .user(userSummary)
                .build();
    }


    public TicketResponse toTicketResponse(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        Seat seat = ticket.getSeat();
        return TicketResponse.builder()
                .id(ticket.getId())
                .seatId(seat != null ? seat.getId() : null)
                .seatCode(seat != null ? seat.getSeatCode() : null)
                .ticketPrice(ticket.getTicketPrice())
                .ticketStatus(ticket.getTicketStatus())
                .qrCode(ticket.getQrCode())
                .createdAt(ticket.getCreatedAt())
                .build();
    }

    public PaymentSummaryResponse toPaymentSummaryResponse(Payment payment) {
        if (payment == null) {
            return null;
        }

        RefundResponse refundResponse = (refundMapper != null && payment.getRefund() != null)
                ? refundMapper.toRefundResponse(payment.getRefund())
                : null;

        return PaymentSummaryResponse.builder()
                .id(payment.getId())
                .paymentMethod(payment.getPaymentMethod())
                .paymentCode(payment.getPaymentCode())
                .gatewayTransactionId(payment.getGatewayTransactionId())
                .amount(payment.getAmount())
                .paymentStatus(payment.getPaymentStatus())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .refund(refundResponse)
                .build();
    }


    public BookingSeatResponse toBookingSeatResponse(Seat seat, BigDecimal price) {
        if (seat == null) {
            return null;
        }

        SeatType seatType = seat.getSeatType();
        return BookingSeatResponse.builder()
                .seatId(seat.getId())
                .rowLabel(seat.getRowLabel())
                .seatNumber(seat.getSeatNumber())
                .seatCode(seat.getSeatCode())
                .seatTypeId(seatType != null ? seatType.getId() : null)
                .seatTypeName(seatType != null ? seatType.getName() : null)
                .price(price)
                .build();
    }
}

