package com.cinebook.task;

import com.cinebook.entity.Booking;
import com.cinebook.enums.BookingStatus;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.SeatHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cinebook.repository.BookingPromotionRepository;
import com.cinebook.repository.PromotionRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingCleanupTaskTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Mock
    private BookingPromotionRepository bookingPromotionRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @InjectMocks
    private BookingCleanupTask bookingCleanupTask;

    private Booking expiredBooking;


    @BeforeEach
    void setUp() {
        expiredBooking = new Booking();
        expiredBooking.setId("expired-1");
        expiredBooking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        expiredBooking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));
    }

    @Test
    void cleanupExpiredBookingsAndHolds_ProcessesExpiredBookings() {
        when(bookingRepository.findExpiredBookings(eq(BookingStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(expiredBooking));
        when(seatHoldRepository.deleteExpiredHolds(any())).thenReturn(2);

        bookingCleanupTask.cleanupExpiredBookingsAndHolds();

        assertEquals(BookingStatus.EXPIRED, expiredBooking.getBookingStatus());
        verify(seatHoldRepository, times(1)).deleteByBookingId("expired-1");
        verify(bookingRepository, times(1)).saveAll(List.of(expiredBooking));
        verify(seatHoldRepository, times(1)).deleteExpiredHolds(any());
    }

    @Test
    void cleanupExpiredBookingsAndHolds_HandlesExceptionGracefully() {
        when(bookingRepository.findExpiredBookings(any(), any()))
                .thenThrow(new RuntimeException("DB Connection Timeout"));

        // Should not throw exception
        bookingCleanupTask.cleanupExpiredBookingsAndHolds();

        verify(bookingRepository, times(1)).findExpiredBookings(any(), any());
    }
}

