package com.cinebook.task;

import com.cinebook.entity.Booking;
import com.cinebook.entity.BookingPromotion;
import com.cinebook.entity.Promotion;
import com.cinebook.enums.BookingStatus;
import com.cinebook.repository.BookingPromotionRepository;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.PromotionRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingCleanupTask bookingCleanupTask;

    private Booking expiredBooking;

    @BeforeEach
    void setUp() {
        expiredBooking = new Booking();
        expiredBooking.setId("expired-1");
        expiredBooking.setBookingCode("CB-TEST-001");
        expiredBooking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        expiredBooking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));
    }

    @Test
    void cleanupExpiredBookingsAndHolds_ProcessesExpiredBookings() {
        Booking expiredResult = new Booking();
        expiredResult.setId("expired-1");
        expiredResult.setBookingStatus(BookingStatus.EXPIRED);

        Page<Booking> firstPage = new PageImpl<>(List.of(expiredBooking));
        when(bookingRepository.findExpiredBookings(eq(BookingStatus.PENDING_PAYMENT), any(), any(Pageable.class)))
                .thenReturn(firstPage);
        when(bookingService.expireBookingIfHoldExpired(expiredBooking)).thenReturn(expiredResult);
        when(bookingService.cleanupExpiredSeatHolds(any())).thenReturn(2);

        bookingCleanupTask.cleanupExpiredBookingsAndHolds();

        verify(bookingService, times(1)).expireBookingIfHoldExpired(expiredBooking);
        verify(bookingService, times(1)).cleanupExpiredSeatHolds(any());
    }

    @Test
    void cleanupExpiredBookingsAndHolds_HandlesExceptionGracefully() {
        when(bookingRepository.findExpiredBookings(any(), any(), any(Pageable.class)))
                .thenThrow(new RuntimeException("DB Connection Timeout"));

        // Should not throw exception
        bookingCleanupTask.cleanupExpiredBookingsAndHolds();

        verify(bookingRepository, times(1)).findExpiredBookings(any(), any(), any(Pageable.class));
    }

    @Test
    void cleanupExpiredBookingsAndHolds_ErrorIsolation_OneBookingErrorDoesNotAbortOthers() {
        Booking bookingA = new Booking();
        bookingA.setId("booking-A");
        bookingA.setBookingCode("CB-A");
        bookingA.setBookingStatus(BookingStatus.PENDING_PAYMENT);

        Booking bookingB = new Booking();
        bookingB.setId("booking-B");
        bookingB.setBookingCode("CB-B");
        bookingB.setBookingStatus(BookingStatus.PENDING_PAYMENT);

        Booking bookingC = new Booking();
        bookingC.setId("booking-C");
        bookingC.setBookingCode("CB-C");
        bookingC.setBookingStatus(BookingStatus.PENDING_PAYMENT);

        Booking expiredA = new Booking();
        expiredA.setId("booking-A");
        expiredA.setBookingStatus(BookingStatus.EXPIRED);

        Booking expiredC = new Booking();
        expiredC.setId("booking-C");
        expiredC.setBookingStatus(BookingStatus.EXPIRED);

        Page<Booking> page = new PageImpl<>(List.of(bookingA, bookingB, bookingC));
        when(bookingRepository.findExpiredBookings(eq(BookingStatus.PENDING_PAYMENT), any(), any(Pageable.class)))
                .thenReturn(page);

        when(bookingService.expireBookingIfHoldExpired(bookingA)).thenReturn(expiredA);
        when(bookingService.expireBookingIfHoldExpired(bookingB)).thenThrow(new RuntimeException("Optimistic lock failure"));
        when(bookingService.expireBookingIfHoldExpired(bookingC)).thenReturn(expiredC);

        bookingCleanupTask.cleanupExpiredBookingsAndHolds();

        // Both A and C are processed despite B throwing an exception
        verify(bookingService, times(1)).expireBookingIfHoldExpired(bookingA);
        verify(bookingService, times(1)).expireBookingIfHoldExpired(bookingB);
        verify(bookingService, times(1)).expireBookingIfHoldExpired(bookingC);
        verify(bookingService, times(1)).cleanupExpiredSeatHolds(any());
    }

    @Test
    void cleanupExpiredBookingsAndHolds_EmptyBatch_CompletesGracefully() {
        when(bookingRepository.findExpiredBookings(eq(BookingStatus.PENDING_PAYMENT), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(bookingService.cleanupExpiredSeatHolds(any())).thenReturn(0);

        bookingCleanupTask.cleanupExpiredBookingsAndHolds();

        verify(bookingService, never()).expireBookingIfHoldExpired(any());
        verify(bookingService, times(1)).cleanupExpiredSeatHolds(any());
    }

    @Test
    void cleanupExpiredBookingsAndHolds_DirectFallbackWhenServiceNull() {
        BookingCleanupTask directTask = new BookingCleanupTask(
                bookingRepository,
                seatHoldRepository,
                bookingPromotionRepository,
                promotionRepository
        );

        Promotion promo = new Promotion();
        promo.setId("promo-1");
        promo.setCode("DISCOUNT20");
        promo.setUsedCount(2);

        BookingPromotion bp = new BookingPromotion();
        bp.setBooking(expiredBooking);
        bp.setPromotion(promo);

        when(bookingRepository.findExpiredBookings(eq(BookingStatus.PENDING_PAYMENT), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expiredBooking)));
        when(bookingPromotionRepository.findByBookingId(expiredBooking.getId())).thenReturn(List.of(bp));
        when(promotionRepository.findByIdWithLock("promo-1")).thenReturn(Optional.of(promo));
        when(seatHoldRepository.deleteExpiredHolds(any())).thenReturn(1);

        directTask.cleanupExpiredBookingsAndHolds();

        assertEquals(BookingStatus.EXPIRED, expiredBooking.getBookingStatus());
        assertEquals(1, promo.getUsedCount());
        verify(seatHoldRepository, times(1)).deleteByBookingId(expiredBooking.getId());
        verify(promotionRepository, times(1)).save(promo);
        verify(bookingRepository, times(1)).save(expiredBooking);
    }
}

