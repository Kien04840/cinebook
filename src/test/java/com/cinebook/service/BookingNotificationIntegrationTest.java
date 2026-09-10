package com.cinebook.service;

import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.response.BookingDetailResponse;
import com.cinebook.dto.response.TicketPricingBreakdown;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.mapper.*;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingNotificationIntegrationTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SeatHoldRepository seatHoldRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private BookingPromotionRepository bookingPromotionRepository;
    @Mock
    private PromotionService promotionService;
    @Mock
    private EmailService emailService;
    @Mock
    private PricingService pricingService;
    @Mock
    private FoodItemRepository foodItemRepository;
    @Mock
    private BookingFoodRepository bookingFoodRepository;
    @Mock
    private NotificationService notificationService;

    @Mock
    private BookingMapper bookingMapper;
    @Mock
    private PromotionMapper promotionMapper;
    @Mock
    private FoodItemMapper foodItemMapper;

    private BookingServiceImpl bookingService;

    private User sampleUser;
    private Showtime sampleShowtime;
    private Auditorium sampleAuditorium;
    private Movie sampleMovie;
    private Cinema sampleCinema;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(
                bookingRepository, seatHoldRepository, ticketRepository, seatRepository,
                showtimeRepository, userRepository, paymentRepository, promotionRepository,
                bookingPromotionRepository, promotionService, bookingMapper, promotionMapper,
                emailService, pricingService, foodItemRepository, bookingFoodRepository,
                foodItemMapper, notificationService
        );

        sampleUser = new User();
        sampleUser.setId("user-1");
        sampleUser.setEmail("customer@test.com");
        sampleUser.setFullName("Nguyen Van Test");
        sampleUser.setStatus(UserStatus.ACTIVE);

        sampleCinema = new Cinema();
        sampleCinema.setId("cinema-1");
        sampleCinema.setName("CineBook Central");
        sampleCinema.setStatus(CinemaStatus.ACTIVE);

        sampleAuditorium = new Auditorium();
        sampleAuditorium.setId("aud-1");
        sampleAuditorium.setName("Screen 1");
        sampleAuditorium.setCinema(sampleCinema);
        sampleAuditorium.setStatus(AuditoriumStatus.ACTIVE);

        sampleMovie = new Movie();
        sampleMovie.setId("movie-1");
        sampleMovie.setTitle("Dune: Part Two");
        sampleMovie.setStatus(MovieStatus.NOW_SHOWING);

        sampleShowtime = new Showtime();
        sampleShowtime.setId("showtime-1");
        sampleShowtime.setMovie(sampleMovie);
        sampleShowtime.setAuditorium(sampleAuditorium);
        sampleShowtime.setStartTime(LocalDateTime.now().plusHours(2));
        sampleShowtime.setEndTime(LocalDateTime.now().plusHours(4));
        sampleShowtime.setBasePrice(new BigDecimal("100000.00"));
        sampleShowtime.setStatus(ShowtimeStatus.SCHEDULED);

        UserDetailsImpl userDetails = new UserDetailsImpl(
                sampleUser.getId(),
                sampleUser.getEmail(),
                "password",
                sampleUser.getFullName(),
                sampleUser.getStatus(),
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        lenient().when(bookingMapper.toBookingDetailResponse(any(), any(), any(), any(), any()))
                .thenReturn(BookingDetailResponse.builder().id("booking-1").build());
        lenient().when(bookingMapper.toBookingDetailResponse(any(), any(), any(), any(), any(), any()))
                .thenReturn(BookingDetailResponse.builder().id("booking-1").build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("confirmPaidBooking triggers PAYMENT_SUCCESS notification exactly once")
    void confirmPaidBooking_TriggersPaymentSuccessNotification() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-2026-001");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));

        Payment payment = new Payment();
        payment.setId("pay-1");
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100000.00"));

        Seat seat = new Seat();
        seat.setId("seat-1");
        seat.setAuditorium(sampleAuditorium);
        seat.setRowLabel("A");
        seat.setSeatNumber((short) 1);

        SeatHold hold = new SeatHold();
        hold.setId(1L);
        hold.setBooking(booking);
        hold.setSeat(seat);
        hold.setShowtime(sampleShowtime);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(hold));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pricingService.calculateShowtimeBaseBreakdown(any())).thenReturn(
                TicketPricingBreakdown.builder().basePrice(new BigDecimal("100000.00")).finalPrice(new BigDecimal("100000.00")).build());
        when(pricingService.calculateTicketPrice(any(TicketPricingBreakdown.class), any())).thenReturn(
                TicketPricingBreakdown.builder().basePrice(new BigDecimal("100000.00")).finalPrice(new BigDecimal("100000.00")).build());
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(List.of(payment));

        BookingDetailResponse response = bookingService.confirmPaidBooking("booking-1", "pay-1");

        assertNotNull(response);

        // Verify notification was triggered
        verify(notificationService, times(1)).createNotification(
                eq(sampleUser),
                eq(booking),
                eq(NotificationType.PAYMENT_SUCCESS),
                eq("Thanh toán thành công"),
                anyString()
        );
    }

    @Test
    @DisplayName("confirmPaidBooking when already PAID returns idempotent result and does NOT trigger duplicate notification")
    void confirmPaidBooking_AlreadyPaid_NoDuplicateNotification() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-2026-001");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setBookingStatus(BookingStatus.PAID);

        Payment payment = new Payment();
        payment.setId("pay-1");
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100000.00"));

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(List.of(payment));

        BookingDetailResponse response = bookingService.confirmPaidBooking("booking-1", "pay-1");

        assertNotNull(response);

        // Verify NO notification was triggered because it was already confirmed
        verify(notificationService, never()).createNotification(any(User.class), any(Booking.class), any(), any(), any());
    }

    @Test
    @DisplayName("confirmPaidBooking succeeds even if notificationService throws exception (Failure Isolation Policy)")
    void confirmPaidBooking_NotificationFailure_DoesNotRollback() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-2026-001");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));

        Payment payment = new Payment();
        payment.setId("pay-1");
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100000.00"));

        SeatHold hold = new SeatHold();
        hold.setId(1L);
        hold.setBooking(booking);
        hold.setSeat(new Seat());
        hold.setShowtime(sampleShowtime);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(hold));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pricingService.calculateShowtimeBaseBreakdown(any())).thenReturn(
                TicketPricingBreakdown.builder().basePrice(new BigDecimal("100000.00")).finalPrice(new BigDecimal("100000.00")).build());
        when(pricingService.calculateTicketPrice(any(TicketPricingBreakdown.class), any())).thenReturn(
                TicketPricingBreakdown.builder().basePrice(new BigDecimal("100000.00")).finalPrice(new BigDecimal("100000.00")).build());
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(List.of(payment));

        // Simulate notification service throwing unexpected exception
        doThrow(new RuntimeException("Notification DB connection timeout"))
                .when(notificationService).createNotification(any(User.class), any(Booking.class), any(), any(), any());

        assertDoesNotThrow(() -> {
            BookingDetailResponse response = bookingService.confirmPaidBooking("booking-1", "pay-1");
            assertNotNull(response);
        });
    }

    @Test
    @DisplayName("cancelBooking triggers BOOKING_CANCELLED notification")
    void cancelBooking_TriggersBookingCancelledNotification() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-2026-001");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(booking));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(sampleUser));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());

        CancelBookingRequest request = new CancelBookingRequest();
        request.setReason("Đổi kế hoạch");

        BookingDetailResponse response = bookingService.cancelBooking("booking-1", request);

        assertNotNull(response);

        verify(notificationService, times(1)).createNotification(
                eq(sampleUser),
                eq(booking),
                eq(NotificationType.BOOKING_CANCELLED),
                eq("Hủy đơn đặt vé thành công"),
                anyString()
        );
    }

    @Test
    @DisplayName("processBookingRefund triggers REFUND_COMPLETED notification")
    void processBookingRefund_TriggersRefundCompletedNotification() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-2026-001");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setBookingStatus(BookingStatus.PAID);

        Ticket ticket = new Ticket();
        ticket.setId("ticket-1");
        ticket.setBooking(booking);
        ticket.setTicketStatus(TicketStatus.VALID);

        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(List.of(ticket));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.processBookingRefund("booking-1", "Sự cố kỹ thuật rạp", "admin-1");

        assertNotNull(response);

        verify(notificationService, times(1)).createNotification(
                eq(sampleUser),
                eq(booking),
                eq(NotificationType.REFUND_COMPLETED),
                eq("Hoàn tiền thành công"),
                anyString()
        );
    }

    @Test
    @DisplayName("processBookingRefund when already REFUNDED is idempotent and does NOT trigger duplicate notification")
    void processBookingRefund_AlreadyRefunded_NoDuplicateNotification() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-2026-001");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setBookingStatus(BookingStatus.REFUNDED);

        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.processBookingRefund("booking-1", "Lý do", "admin-1");

        assertNotNull(response);

        verify(notificationService, never()).createNotification(any(User.class), any(Booking.class), any(), any(), any());
    }
}
