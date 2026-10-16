package com.cinebook.task;

import com.cinebook.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketExpirationTaskTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private TicketExpirationTask ticketExpirationTask;

    @Test
    void expirePastTickets_Success() {
        when(bookingService.expirePastTickets(any(LocalDateTime.class))).thenReturn(15);

        ticketExpirationTask.expirePastTickets();

        verify(bookingService, times(1)).expirePastTickets(any(LocalDateTime.class));
    }

    @Test
    void expirePastTickets_HandlesExceptionGracefully() {
        when(bookingService.expirePastTickets(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Should not bubble up exception
        ticketExpirationTask.expirePastTickets();

        verify(bookingService, times(1)).expirePastTickets(any(LocalDateTime.class));
    }
}

