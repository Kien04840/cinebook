package com.cinebook.service;

import com.cinebook.config.VnPayConfig;
import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.request.InitiatePaymentRequest;
import com.cinebook.dto.response.*;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.BadRequestException;
import com.cinebook.mapper.*;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.BookingServiceImpl;
import com.cinebook.service.impl.PaymentServiceImpl;
import com.cinebook.service.impl.VnPayServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingPaymentResumeIntegrationTest {

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

    @Spy
    private GenreMapper genreMapper = new GenreMapper();

    @Spy
    private SeatMapper seatMapper = new SeatMapper();

    @Spy
    private PromotionMapper promotionMapper = new PromotionMapper();

    private MovieMapper movieMapper;
    private AuditoriumMapper auditoriumMapper;
    private CinemaMapper cinemaMapper;
    private ShowtimeMapper showtimeMapper;
    private BookingMapper bookingMapper;

    private VnPayConfig vnPayConfig;
    private VnPayService vnPayService;
    private BookingServiceImpl bookingService;
    private PaymentServiceImpl paymentService;

    private User customer;
    private Showtime showtime;
    private Auditorium auditorium;
    private Seat seatA1;
    private Seat seatA2;

    @BeforeEach
    void setUp() {
        movieMapper = new MovieMapper(genreMapper);
        auditoriumMapper = new AuditoriumMapper(seatMapper);
        cinemaMapper = new CinemaMapper(auditoriumMapper);
        showtimeMapper = new ShowtimeMapper(movieMapper, cinemaMapper, auditoriumMapper);
        bookingMapper = new BookingMapper(showtimeMapper);

        vnPayConfig = new VnPayConfig();
        vnPayConfig.setTmnCode("2QXUI4J4");
        vnPayConfig.setHashSecret("RAIUBACKMSUTTBDXGSZLGXDTZUXISGEX");
        vnPayConfig.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnPayConfig.setReturnUrl("http://localhost:5173/payment/result");
        vnPayConfig.setVersion("2.1.0");
        vnPayConfig.setCommand("pay");
        vnPayConfig.setOrderType("other");

        vnPayService = new VnPayServiceImpl(vnPayConfig);

        bookingService = new BookingServiceImpl(
                bookingRepository,
                seatHoldRepository,
                ticketRepository,
                seatRepository,
                showtimeRepository,
                userRepository,
                paymentRepository,
                promotionRepository,
                bookingPromotionRepository,
                promotionService,
                bookingMapper,
                promotionMapper,
                emailService
        );

        paymentService = new PaymentServiceImpl(
                vnPayConfig,
                vnPayService,
                bookingService,
                bookingRepository,
                paymentRepository,
                seatHoldRepository,
                refundRepositoryMock(),
                ticketRepository,
                bookingMapper,
                refundMapperMock(),
                emailService
        );

        customer = new User();
        customer.setId("user-cust-1");
        customer.setEmail("customer@test.com");
        customer.setFullName("Nguyen Customer");
        customer.setStatus(UserStatus.ACTIVE);

        Cinema cinema = new Cinema();
        cinema.setId("cin-1");
        cinema.setName("CineBook Landmark");
        cinema.setStatus(CinemaStatus.ACTIVE);

        auditorium = new Auditorium();
        auditorium.setId("aud-1");
        auditorium.setName("Screen 1");
        auditorium.setCinema(cinema);
        auditorium.setStatus(AuditoriumStatus.ACTIVE);

        Movie movie = new Movie();
        movie.setId("mov-1");
        movie.setTitle("Dune: Part Two");
        movie.setStatus(MovieStatus.NOW_SHOWING);

        showtime = new Showtime();
        showtime.setId("st-1");
        showtime.setMovie(movie);
        showtime.setAuditorium(auditorium);
        showtime.setBasePrice(new BigDecimal("100000.00"));
        showtime.setStatus(ShowtimeStatus.SCHEDULED);
        showtime.setStartTime(LocalDateTime.now().plusDays(1));
        showtime.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));

        SeatType standardType = new SeatType();
        standardType.setId("st-std");
        standardType.setName("STANDARD");
        standardType.setPriceModifier(BigDecimal.ZERO);

        seatA1 = new Seat();
        seatA1.setId("seat-a1");
        seatA1.setRowLabel("A");
        seatA1.setSeatNumber((short) 1);
        seatA1.setSeatType(standardType);
        seatA1.setAuditorium(auditorium);
        seatA1.setStatus(SeatStatus.ACTIVE);

        seatA2 = new Seat();
        seatA2.setId("seat-a2");
        seatA2.setRowLabel("A");
        seatA2.setSeatNumber((short) 2);
        seatA2.setSeatType(standardType);
        seatA2.setAuditorium(auditorium);
        seatA2.setStatus(SeatStatus.ACTIVE);

        mockAuthentication(customer);
    }

    private RefundRepository refundRepositoryMock() {
        return mock(RefundRepository.class);
    }

    private RefundMapper refundMapperMock() {
        return mock(RefundMapper.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthentication(User user) {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        SecurityContextHolder.setContext(securityContext);
    }

    // =========================================================================
    // FLOW A: Navigation Resume
    // =========================================================================
    @Test
    @DisplayName("Flow A: User navigates away and returns to same showtime -> Holds are identified as owned and booking is resumed")
    void testFlowA_NavigationResume() {
        Booking activeBooking = new Booking();
        activeBooking.setId("b-active-1");
        activeBooking.setBookingCode("CB-RESUME-001");
        activeBooking.setUser(customer);
        activeBooking.setShowtime(showtime);
        activeBooking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        activeBooking.setTotalAmount(new BigDecimal("200000.00"));
        activeBooking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(4));

        SeatHold hold1 = new SeatHold();
        hold1.setId(101L);
        hold1.setBooking(activeBooking);
        hold1.setSeat(seatA1);
        hold1.setShowtime(showtime);
        hold1.setExpiresAt(activeBooking.getHoldExpiresAt());

        SeatHold hold2 = new SeatHold();
        hold2.setId(102L);
        hold2.setBooking(activeBooking);
        hold2.setSeat(seatA2);
        hold2.setShowtime(showtime);
        hold2.setExpiresAt(activeBooking.getHoldExpiresAt());

        // 1. Check seat availability for this showtime:
        when(showtimeRepository.findById("st-1")).thenReturn(Optional.of(showtime));
        when(seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc("aud-1")).thenReturn(List.of(seatA1, seatA2));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("st-1"), any(LocalDateTime.class)))
                .thenReturn(List.of(hold1, hold2));
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(anyString(), any())).thenReturn(List.of());

        List<ShowtimeSeatStatusResponse> seatResponses = bookingService.getShowtimeSeatAvailability("st-1");

        assertThat(seatResponses).hasSize(2);
        assertThat(seatResponses).allMatch(s -> s.getAvailabilityStatus() == SeatAvailabilityStatus.HELD);
        assertThat(seatResponses).allMatch(ShowtimeSeatStatusResponse::getIsHeldByCurrentUser);

        // 2. Fetch active booking for showtime:
        when(bookingRepository.findActiveBookingsByUserAndShowtime(eq(customer.getId()), eq("st-1"), any(LocalDateTime.class)))
                .thenReturn(List.of(activeBooking));
        when(seatHoldRepository.findByBookingId("b-active-1")).thenReturn(List.of(hold1, hold2));
        when(paymentRepository.findByBookingId("b-active-1")).thenReturn(List.of());

        BookingDetailResponse activeDetail = bookingService.getActiveBookingForShowtime("st-1");
        assertThat(activeDetail).isNotNull();
        assertThat(activeDetail.getBookingCode()).isEqualTo("CB-RESUME-001");
        assertThat(activeDetail.getSeats()).hasSize(2);

        // 3. Re-submitting createBooking with exact same seats is idempotent:
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(seatRepository.findByIdIn(anySet())).thenReturn(List.of(seatA1, seatA2));

        CreateBookingRequest createReq = CreateBookingRequest.builder()
                .showtimeId("st-1")
                .seatIds(List.of("seat-a1", "seat-a2"))
                .build();

        BookingDetailResponse reCreateResponse = bookingService.createBooking(createReq);
        assertThat(reCreateResponse.getId()).isEqualTo(activeBooking.getId());
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    // =========================================================================
    // FLOW B: Payment Resume
    // =========================================================================
    @Test
    @DisplayName("Flow B: My Bookings Pay -> Resumes existing PENDING payment without 409 Conflict")
    void testFlowB_PaymentResume() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-2026-001");
        booking.setUser(customer);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("200000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(4));
        booking.setShowtime(showtime);

        SeatHold hold = new SeatHold();
        hold.setId(1L);
        hold.setBooking(booking);
        hold.setSeat(seatA1);
        hold.setExpiresAt(booking.getHoldExpiresAt());

        Payment existingPayment = new Payment();
        existingPayment.setId("payment-1");
        existingPayment.setBooking(booking);
        existingPayment.setPaymentCode("PAY-20260901-001");
        existingPayment.setAmount(new BigDecimal("200000.00"));
        existingPayment.setPaymentStatus(PaymentStatus.PENDING);
        existingPayment.setPaymentMethod(PaymentMethod.VNPAY);

        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(booking));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(hold));
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(List.of(existingPayment));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        InitiatePaymentResponse response = paymentService.initiatePayment(
                "booking-1",
                new InitiatePaymentRequest(PaymentMethod.VNPAY),
                request
        );

        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo("payment-1");
        assertThat(response.getPaymentCode()).isEqualTo("PAY-20260901-001");
        assertThat(response.getPaymentUrl()).contains("vnp_SecureHash=");
        assertThat(response.getPaymentUrl()).contains("vnp_Amount=20000000");

        // Verify NO duplicate payment was created
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    // =========================================================================
    // FLOW C & D: VNPay Cancel -> Retry creates new attempt
    // =========================================================================
    @Test
    @DisplayName("Flow D: User cancels VNPay -> Retry allows generating new payment attempt")
    void testFlowD_CancelAndRetry() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-2026-001");
        booking.setUser(customer);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("200000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(4));
        booking.setShowtime(showtime);

        SeatHold hold = new SeatHold();
        hold.setId(1L);
        hold.setBooking(booking);
        hold.setSeat(seatA1);
        hold.setExpiresAt(booking.getHoldExpiresAt());

        Payment cancelledPayment = new Payment();
        cancelledPayment.setId("payment-cancelled");
        cancelledPayment.setBooking(booking);
        cancelledPayment.setPaymentCode("PAY-OLD-CANCELLED");
        cancelledPayment.setAmount(new BigDecimal("200000.00"));
        cancelledPayment.setPaymentStatus(PaymentStatus.CANCELLED);

        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(booking));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(hold));
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(List.of(cancelledPayment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        InitiatePaymentResponse retryResponse = paymentService.initiatePayment(
                "booking-1",
                new InitiatePaymentRequest(PaymentMethod.VNPAY),
                request
        );

        assertThat(retryResponse).isNotNull();
        assertThat(retryResponse.getPaymentCode()).isNotEqualTo("PAY-OLD-CANCELLED");
        assertThat(retryResponse.getPaymentUrl()).contains("vnp_SecureHash=");

        verify(paymentRepository, times(1)).saveAndFlush(any(Payment.class));
    }

    // =========================================================================
    // FLOW F: Hold Expiration
    // =========================================================================
    @Test
    @DisplayName("Flow F: Expired hold causes lazy expiration and rejection on payment initiation")
    void testFlowF_ExpiredHoldRejection() {
        Booking booking = new Booking();
        booking.setId("booking-expired");
        booking.setBookingCode("CB-EXPIRED-001");
        booking.setUser(customer);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("200000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired

        when(bookingRepository.findByIdWithLock("booking-expired")).thenReturn(Optional.of(booking));

        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> paymentService.initiatePayment(
                "booking-expired",
                new InitiatePaymentRequest(PaymentMethod.VNPAY),
                request
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Đơn đặt vé đã hết hạn giữ chỗ");

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(seatHoldRepository).deleteByBookingId("booking-expired");
    }
}
