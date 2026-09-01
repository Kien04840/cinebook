package com.cinebook.service;

import com.cinebook.entity.*;
import com.cinebook.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @InjectMocks
    private EmailServiceImpl emailService;

    @Test
    @DisplayName("sendPasswordResetEmail - Executes safely without unhandled exception")
    void testSendPasswordResetEmail_Success() {
        assertThatCode(() -> emailService.sendPasswordResetEmail("customer@cinebook.com", "sample-reset-token"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendBookingConfirmationEmail - Executes safely without unhandled exception")
    void testSendBookingConfirmationEmail_Success() {
        Movie movie = new Movie();
        movie.setTitle("Dune: Part Two");

        Auditorium auditorium = new Auditorium();
        auditorium.setName("Screen 1");

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setAuditorium(auditorium);
        showtime.setStartTime(LocalDateTime.now().plusDays(1));

        Booking booking = new Booking();
        booking.setBookingCode("CB-20260901-XYZ123");
        booking.setTotalAmount(new BigDecimal("150000.00"));
        booking.setShowtime(showtime);

        Seat seat = new Seat();
        seat.setRowLabel("B");
        seat.setSeatNumber((short) 4);

        Ticket ticket = new Ticket();
        ticket.setSeat(seat);

        assertThatCode(() -> emailService.sendBookingConfirmationEmail("customer@cinebook.com", "Nguyen Van A", booking, List.of(ticket)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendRefundConfirmationEmail - Executes safely without unhandled exception")
    void testSendRefundConfirmationEmail_Success() {
        Movie movie = new Movie();
        movie.setTitle("Dune: Part Two");

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setStartTime(LocalDateTime.now().plusDays(1));

        Booking booking = new Booking();
        booking.setBookingCode("CB-20260901-XYZ123");
        booking.setShowtime(showtime);

        Refund refund = new Refund();
        refund.setRefundCode("REF-20260901-ABC999");
        refund.setAmount(new BigDecimal("150000.00"));
        refund.setRefundReason("Khách hàng bận việc đột xuất");
        refund.setProcessedAt(LocalDateTime.now());

        assertThatCode(() -> emailService.sendRefundConfirmationEmail("customer@cinebook.com", "Nguyen Van A", booking, refund))
                .doesNotThrowAnyException();
    }
}
