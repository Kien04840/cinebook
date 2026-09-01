package com.cinebook.service;

import com.cinebook.config.VnPayConfig;
import com.cinebook.dto.request.RefundRequest;
import com.cinebook.dto.response.RefundResponse;
import com.cinebook.dto.response.ShowtimeSeatStatusResponse;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.mapper.BookingMapper;
import com.cinebook.mapper.PromotionMapper;
import com.cinebook.mapper.RefundMapper;
import com.cinebook.mapper.ShowtimeMapper;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.BookingServiceImpl;
import com.cinebook.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRefundIntegrationTest {

    @Mock
    private VnPayConfig vnPayConfig;

    @Mock
    private VnPayService vnPayService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

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
    private PromotionRepository promotionRepository;

    @Mock
    private BookingPromotionRepository bookingPromotionRepository;

    @Mock
    private PromotionService promotionService;

    @Mock
    private PromotionMapper promotionMapper;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private ShowtimeMapper showtimeMapper;

    @Mock
    private EmailService emailService;

    private RefundMapper refundMapper;
    private BookingMapper bookingMapper;
    private BookingServiceImpl bookingService;
    private PaymentServiceImpl paymentService;

    private User testCustomer;
    private User testAdmin;
    private Showtime testShowtime;
    private Auditorium testAuditorium;
    private Seat testSeat;
    private Booking testBooking;
    private Payment testPayment;
    private Ticket testTicket;

    @BeforeEach
    void setUp() {
        refundMapper = new RefundMapper();
        bookingMapper = new BookingMapper(showtimeMapper, refundMapper);

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
                refundRepository,
                ticketRepository,
                bookingMapper,
                refundMapper,
                emailService
        );

        testCustomer = new User();
        testCustomer.setId("cust-1");
        testCustomer.setEmail("cust@cinebook.com");
        testCustomer.setFullName("Test Customer");

        testAdmin = new User();
        testAdmin.setId("admin-1");
        testAdmin.setEmail("admin@cinebook.com");
        testAdmin.setFullName("Admin User");

        testAuditorium = new Auditorium();
        testAuditorium.setId("aud-1");
        testAuditorium.setName("Screen 1");
        testAuditorium.setStatus(AuditoriumStatus.ACTIVE);

        testSeat = new Seat();
        testSeat.setId("seat-1");
        testSeat.setRowLabel("A");
        testSeat.setSeatNumber((short) 1);
        testSeat.setStatus(SeatStatus.ACTIVE);
        testSeat.setAuditorium(testAuditorium);


        testShowtime = new Showtime();
        testShowtime.setId("showtime-1");
        testShowtime.setAuditorium(testAuditorium);
        testShowtime.setStartTime(LocalDateTime.now().plusHours(5));
        testShowtime.setStatus(ShowtimeStatus.SCHEDULED);
        testShowtime.setBasePrice(new BigDecimal("100000.00"));

        testBooking = new Booking();
        testBooking.setId("booking-1");
        testBooking.setBookingCode("CB-20260901-001");
        testBooking.setUser(testCustomer);
        testBooking.setShowtime(testShowtime);
        testBooking.setBookingStatus(BookingStatus.PAID);
        testBooking.setTotalAmount(new BigDecimal("100000.00"));

        testPayment = new Payment();
        testPayment.setId("pay-1");
        testPayment.setBooking(testBooking);
        testPayment.setPaymentCode("PAY-20260901-ABC");
        testPayment.setGatewayTransactionId("VNP-TRANS-123456");
        testPayment.setAmount(new BigDecimal("100000.00"));
        testPayment.setPaymentStatus(PaymentStatus.SUCCESS);
        testPayment.setPaidAt(LocalDateTime.now().minusHours(1));

        testTicket = new Ticket();
        testTicket.setId("ticket-1");
        testTicket.setBooking(testBooking);
        testTicket.setSeat(testSeat);
        testTicket.setTicketPrice(new BigDecimal("100000.00"));
        testTicket.setTicketStatus(TicketStatus.VALID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthentication(User user, String role) {
        String authName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        UserDetailsImpl userDetails = new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                "password",
                user.getFullName(),
                UserStatus.ACTIVE,
                true,
                List.of(new SimpleGrantedAuthority(authName))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("E2E Refund Flow: Customer refunds paid booking -> Payment=REFUNDED, Booking=REFUNDED, Ticket=CANCELLED, Quota NOT restored")
    void testCustomerFullRefundIntegration() {
        mockAuthentication(testCustomer, "CUSTOMER");

        when(paymentRepository.findByIdWithLock("pay-1")).thenReturn(Optional.of(testPayment));
        when(refundRepository.findByPaymentId("pay-1")).thenReturn(Optional.empty());
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(List.of(testTicket));
        when(refundRepository.existsByRefundCode(anyString())).thenReturn(false);
        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, String> gatewaySuccess = new HashMap<>();
        gatewaySuccess.put("vnp_ResponseCode", "00");
        gatewaySuccess.put("vnp_ResponseId", "VNP-REF-888");
        gatewaySuccess.put("vnp_Message", "Success");
        when(vnPayService.refundPayment(any(), any(), any(), any())).thenReturn(gatewaySuccess);

        when(refundRepository.findById(anyString())).thenAnswer(i -> {
            Refund r = new Refund();
            r.setId(i.getArgument(0));
            r.setPayment(testPayment);
            r.setAmount(testPayment.getAmount());
            r.setRefundStatus(RefundStatus.PENDING);
            return Optional.of(r);
        });

        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(testBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse refundResponse = paymentService.refundPayment("pay-1", new RefundRequest("Khách hủy vé"), new MockHttpServletRequest());

        assertThat(refundResponse).isNotNull();
        assertThat(refundResponse.getRefundStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(testBooking.getBookingStatus()).isEqualTo(BookingStatus.REFUNDED);
        assertThat(testTicket.getTicketStatus()).isEqualTo(TicketStatus.CANCELLED);

        // Verify promotion is NEVER modified or restored on refund
        verify(promotionRepository, never()).save(any());
    }


    @Test
    @DisplayName("Seat availability after refund: Cancelled ticket is not in SOLD status -> Seat is AVAILABLE")
    void testSeatAvailabilityAfterRefund() {
        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(testShowtime));
        when(seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc("aud-1")).thenReturn(List.of(testSeat));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());

        // When ticket is CANCELLED, findTicketsByShowtimeIdAndStatuses returns empty list because it only searches VALID/USED
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(Collections.emptyList());

        List<ShowtimeSeatStatusResponse> availability = bookingService.getShowtimeSeatAvailability("showtime-1");

        assertThat(availability).hasSize(1);
        assertThat(availability.get(0).getAvailabilityStatus()).isEqualTo(SeatAvailabilityStatus.AVAILABLE);
    }
}
