package com.cinebook.service;

import com.cinebook.dto.response.TicketCheckInResponse;
import com.cinebook.dto.response.TicketVerifyResponse;
import com.cinebook.entity.*;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.enums.TicketStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.repository.TicketRepository;
import com.cinebook.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private Ticket sampleTicket;
    private Booking sampleBooking;
    private Showtime sampleShowtime;
    private Movie sampleMovie;
    private Seat sampleSeat;

    @BeforeEach
    void setUp() {
        sampleMovie = new Movie();
        sampleMovie.setId("movie-1");
        sampleMovie.setTitle("Avengers: Endgame");

        sampleShowtime = new Showtime();
        sampleShowtime.setId("showtime-1");
        sampleShowtime.setMovie(sampleMovie);
        sampleShowtime.setStatus(ShowtimeStatus.SCHEDULED);
        sampleShowtime.setStartTime(LocalDateTime.now().plusHours(3));
        sampleShowtime.setEndTime(LocalDateTime.now().plusHours(6));

        sampleBooking = new Booking();
        sampleBooking.setId("booking-1");
        sampleBooking.setBookingCode("CB-20260901-TICKET1");
        sampleBooking.setBookingStatus(BookingStatus.PAID);
        sampleBooking.setShowtime(sampleShowtime);

        sampleSeat = new Seat();
        sampleSeat.setId("seat-1");
        sampleSeat.setRowLabel("A");
        sampleSeat.setSeatNumber((short) 1);

        sampleTicket = new Ticket();
        sampleTicket.setId("ticket-1");
        sampleTicket.setQrCode("ticket-1-qr");
        sampleTicket.setTicketStatus(TicketStatus.VALID);
        sampleTicket.setTicketPrice(new BigDecimal("100000.00"));
        sampleTicket.setBooking(sampleBooking);
        sampleTicket.setSeat(sampleSeat);
    }

    @Test
    @DisplayName("verifyTicket - Valid ticket returns checkInEligible = true")
    void testVerifyTicket_Valid_ReturnsEligible() {
        when(ticketRepository.findByQrCode("ticket-1-qr")).thenReturn(Optional.of(sampleTicket));

        TicketVerifyResponse response = ticketService.verifyTicket("ticket-1-qr");

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo("ticket-1");
        assertThat(response.isCheckInEligible()).isTrue();
        assertThat(response.getIneligibleReason()).isNull();
        assertThat(response.getMovieTitle()).isEqualTo("Avengers: Endgame");
        assertThat(response.getSeatCode()).isEqualTo("A1");
    }

    @Test
    @DisplayName("verifyTicket - Already USED ticket returns checkInEligible = false with reason")
    void testVerifyTicket_Used_ReturnsIneligible() {
        sampleTicket.setTicketStatus(TicketStatus.USED);
        when(ticketRepository.findByQrCode("ticket-1-qr")).thenReturn(Optional.of(sampleTicket));

        TicketVerifyResponse response = ticketService.verifyTicket("ticket-1-qr");

        assertThat(response).isNotNull();
        assertThat(response.isCheckInEligible()).isFalse();
        assertThat(response.getIneligibleReason()).contains("đã được sử dụng");
    }

    @Test
    @DisplayName("verifyTicket - Cancelled / Refunded booking returns checkInEligible = false")
    void testVerifyTicket_Cancelled_ReturnsIneligible() {
        sampleTicket.setTicketStatus(TicketStatus.CANCELLED);
        sampleBooking.setBookingStatus(BookingStatus.REFUNDED);
        when(ticketRepository.findByQrCode("ticket-1-qr")).thenReturn(Optional.of(sampleTicket));

        TicketVerifyResponse response = ticketService.verifyTicket("ticket-1-qr");

        assertThat(response).isNotNull();
        assertThat(response.isCheckInEligible()).isFalse();
        assertThat(response.getIneligibleReason()).contains("hủy");
    }

    @Test
    @DisplayName("verifyTicket - Not found ticket throws ResourceNotFoundException")
    void testVerifyTicket_NotFound_ThrowsException() {
        when(ticketRepository.findByQrCode("invalid-qr")).thenReturn(Optional.empty());
        when(ticketRepository.findById("invalid-qr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.verifyTicket("invalid-qr"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy thông tin vé");
    }

    @Test
    @DisplayName("checkInTicket - Valid ticket atomic transition to USED succeeds")
    void testCheckInTicket_Valid_TransitionsToUsed() {
        when(ticketRepository.findByIdWithLock("ticket-1")).thenReturn(Optional.of(sampleTicket));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketCheckInResponse response = ticketService.checkInTicket("ticket-1");

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo("ticket-1");
        assertThat(response.getTicketStatus()).isEqualTo(TicketStatus.USED);
        assertThat(response.getMessage()).contains("Soát vé thành công");
        assertThat(sampleTicket.getTicketStatus()).isEqualTo(TicketStatus.USED);

        verify(ticketRepository).saveAndFlush(sampleTicket);
    }

    @Test
    @DisplayName("checkInTicket - Already USED ticket throws ConflictException (double scan protection)")
    void testCheckInTicket_AlreadyUsed_ThrowsConflict() {
        sampleTicket.setTicketStatus(TicketStatus.USED);
        when(ticketRepository.findByIdWithLock("ticket-1")).thenReturn(Optional.of(sampleTicket));

        assertThatThrownBy(() -> ticketService.checkInTicket("ticket-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã được soát trước đó");

        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("checkInTicket - Cancelled ticket throws BadRequestException")
    void testCheckInTicket_Cancelled_ThrowsBadRequest() {
        sampleTicket.setTicketStatus(TicketStatus.CANCELLED);
        when(ticketRepository.findByIdWithLock("ticket-1")).thenReturn(Optional.of(sampleTicket));

        assertThatThrownBy(() -> ticketService.checkInTicket("ticket-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã bị hủy");

        verify(ticketRepository, never()).saveAndFlush(any());
    }
}
