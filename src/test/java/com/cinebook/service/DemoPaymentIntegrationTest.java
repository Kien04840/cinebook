package com.cinebook.service;

import com.cinebook.config.VnPayConfig;
import com.cinebook.controller.DemoPaymentController;
import com.cinebook.dto.request.DemoPaymentCompleteRequest;
import com.cinebook.dto.response.DemoPaymentCompleteResponse;
import com.cinebook.entity.*;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.mapper.BookingMapper;
import com.cinebook.mapper.RefundMapper;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.MockVnPayService;
import com.cinebook.service.impl.PaymentServiceImpl;
import com.cinebook.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoPaymentIntegrationTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private RefundMapper refundMapper;

    @Mock
    private EmailService emailService;

    @Mock
    private BookingService bookingService;

    private VnPayConfig vnPayConfig;
    private MockVnPayService mockVnPayService;
    private PaymentServiceImpl paymentService;
    private DemoPaymentController demoPaymentController;

    private MockedStatic<SecurityUtils> mockedSecurityUtils;
    private User testUser;
    private Booking testBooking;
    private Payment testPayment;
    private SeatHold testSeatHold;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        mockedSecurityUtils = mockStatic(SecurityUtils.class);

        vnPayConfig = new VnPayConfig();
        vnPayConfig.setTmnCode("MOCK_TMN");
        vnPayConfig.setHashSecret("MOCK_HASH_SECRET_KEY_12345678901234567890");
        vnPayConfig.setVersion("2.1.0");
        vnPayConfig.setCommand("pay");
        vnPayConfig.setOrderType("other");
        vnPayConfig.setReturnUrl("http://localhost:8080/api/v1/payments/vnpay/return");

        mockVnPayService = new MockVnPayService(vnPayConfig);
        ReflectionTestUtils.setField(mockVnPayService, "frontendUrl", "http://localhost:5173");

        paymentService = new PaymentServiceImpl(
                vnPayConfig,
                mockVnPayService,
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

        demoPaymentController = new DemoPaymentController(
                paymentRepository,
                paymentService,
                mockVnPayService,
                vnPayConfig,
                seatHoldRepository
        );

        testUser = new User();
        testUser.setId("usr-1111-2222");
        testUser.setEmail("customer@example.com");
        testUser.setFullName("Customer Demo");

        testBooking = new Booking();
        testBooking.setId("bk-1111-2222");
        testBooking.setBookingCode("BK-20260906-001");
        testBooking.setUser(testUser);
        testBooking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        testBooking.setTotalAmount(new BigDecimal("150000.00"));
        testBooking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));

        testPayment = new Payment();
        testPayment.setId("pay-id-111");
        testPayment.setPaymentCode("PAY-20260906-001");
        testPayment.setBooking(testBooking);
        testPayment.setAmount(new BigDecimal("150000.00"));
        testPayment.setPaymentMethod(PaymentMethod.VNPAY);
        testPayment.setPaymentStatus(PaymentStatus.PENDING);
        testPayment.setCreatedAt(LocalDateTime.now());

        testSeatHold = new SeatHold();
        testSeatHold.setId(1L);
        testSeatHold.setBooking(testBooking);
        testSeatHold.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        userDetails = UserDetailsImpl.builder()
                .id(testUser.getId())
                .email(testUser.getEmail())
                .fullName("Customer Demo")
                .password("encoded_pass")
                .status(com.cinebook.enums.UserStatus.ACTIVE)
                .isDeleted(false)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();

        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails)
                .thenReturn(Optional.of(userDetails));
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("MockVnPayService generates mock payment URL directed to frontend /payment/demo")
    void buildPaymentUrl_ShouldPointToFrontendDemo() {
        String paymentUrl = mockVnPayService.buildPaymentUrl(testPayment, testBooking, "127.0.0.1");

        assertThat(paymentUrl).startsWith("http://localhost:5173/payment/demo?");
        assertThat(paymentUrl).contains("vnp_TxnRef=PAY-20260906-001");
        assertThat(paymentUrl).contains("bookingCode=BK-20260906-001");
        assertThat(paymentUrl).contains("bookingId=bk-1111-2222");
        assertThat(paymentUrl).contains("vnp_SecureHash=");
    }

    @Test
    @DisplayName("Demo payment complete with 00 (Success) updates payment to SUCCESS and confirms booking")
    void completeDemoPayment_Success_ShouldConfirmBookingAndReturnSuccessResult() {
        when(paymentRepository.findByPaymentCode(testPayment.getPaymentCode()))
                .thenReturn(Optional.of(testPayment));
        when(seatHoldRepository.findByBookingId(testBooking.getId()))
                .thenReturn(List.of(testSeatHold));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode(testPayment.getPaymentCode())
                .responseCode("00")
                .build();

        ResponseEntity<DemoPaymentCompleteResponse> response = demoPaymentController.completeDemoPayment(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        DemoPaymentCompleteResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getResponseCode()).isEqualTo("00");
        assertThat(body.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(body.getRedirectUrl()).startsWith("/payment/result?");
        assertThat(body.getRedirectUrl()).contains("vnp_ResponseCode=00");
        assertThat(body.getRedirectUrl()).contains("vnp_SecureHash=");

        // Verify bookingService.confirmPaidBooking was called with bookingId and paymentId
        verify(bookingService, times(1)).confirmPaidBooking(testBooking.getId(), testPayment.getId());
        // Verify payment is marked SUCCESS
        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository, atLeastOnce()).saveAndFlush(testPayment);
    }

    @Test
    @DisplayName("Demo payment complete with 24 (Customer Cancel) cancels payment")
    void completeDemoPayment_Cancel_ShouldCancelPayment() {
        when(paymentRepository.findByPaymentCode(testPayment.getPaymentCode()))
                .thenReturn(Optional.of(testPayment));
        when(seatHoldRepository.findByBookingId(testBooking.getId()))
                .thenReturn(List.of(testSeatHold));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode(testPayment.getPaymentCode())
                .responseCode("24")
                .build();

        ResponseEntity<DemoPaymentCompleteResponse> response = demoPaymentController.completeDemoPayment(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        DemoPaymentCompleteResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getResponseCode()).isEqualTo("24");
        assertThat(body.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(body.getRedirectUrl()).startsWith("/payment/result?");
        assertThat(body.getRedirectUrl()).contains("vnp_ResponseCode=24");

        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(bookingService, never()).confirmPaidBooking(anyString(), anyString());
    }

    @Test
    @DisplayName("Demo payment complete with 07 (Failure) marks payment as FAILED")
    void completeDemoPayment_Failure_ShouldFailPayment() {
        when(paymentRepository.findByPaymentCode(testPayment.getPaymentCode()))
                .thenReturn(Optional.of(testPayment));
        when(seatHoldRepository.findByBookingId(testBooking.getId()))
                .thenReturn(List.of(testSeatHold));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode(testPayment.getPaymentCode())
                .responseCode("07")
                .build();

        ResponseEntity<DemoPaymentCompleteResponse> response = demoPaymentController.completeDemoPayment(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        DemoPaymentCompleteResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getResponseCode()).isEqualTo("07");
        assertThat(body.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(body.getRedirectUrl()).startsWith("/payment/result?");
        assertThat(body.getRedirectUrl()).contains("vnp_ResponseCode=07");

        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(bookingService, never()).confirmPaidBooking(anyString(), anyString());
    }

    @Test
    @DisplayName("Demo payment complete is idempotent when already SUCCESS")
    void completeDemoPayment_Idempotent_WhenAlreadySuccess() {
        testPayment.setPaymentStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findByPaymentCode(testPayment.getPaymentCode()))
                .thenReturn(Optional.of(testPayment));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode(testPayment.getPaymentCode())
                .responseCode("00")
                .build();

        ResponseEntity<DemoPaymentCompleteResponse> response = demoPaymentController.completeDemoPayment(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        DemoPaymentCompleteResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(body.getRedirectUrl()).contains("vnp_ResponseCode=00");

        verify(bookingService, never()).confirmPaidBooking(anyString(), anyString());
    }

    @Test
    @DisplayName("Demo payment retry flow: After failed attempt, user retries with a new payment session that succeeds")
    void completeDemoPayment_RetryFlow_SucceedsAfterFailure() {
        // Step 1: Initial payment fails with code 07
        when(paymentRepository.findByPaymentCode(testPayment.getPaymentCode()))
                .thenReturn(Optional.of(testPayment));
        when(seatHoldRepository.findByBookingId(testBooking.getId()))
                .thenReturn(List.of(testSeatHold));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DemoPaymentCompleteRequest failRequest = DemoPaymentCompleteRequest.builder()
                .paymentCode(testPayment.getPaymentCode())
                .responseCode("07")
                .build();

        ResponseEntity<DemoPaymentCompleteResponse> failResponse = demoPaymentController.completeDemoPayment(failRequest);
        assertThat(failResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(bookingService, never()).confirmPaidBooking(anyString(), anyString());

        // Step 2: New retry payment session is created for this booking (PENDING)
        Payment retryPayment = new Payment();
        retryPayment.setId("pay-retry-222");
        retryPayment.setPaymentCode("PAY-20260906-RETRY");
        retryPayment.setBooking(testBooking);
        retryPayment.setAmount(new BigDecimal("150000.00"));
        retryPayment.setPaymentMethod(PaymentMethod.VNPAY);
        retryPayment.setPaymentStatus(PaymentStatus.PENDING);
        retryPayment.setCreatedAt(LocalDateTime.now());

        when(paymentRepository.findByPaymentCode(retryPayment.getPaymentCode()))
                .thenReturn(Optional.of(retryPayment));

        // Step 3: Complete retry payment with 00 (Success)
        DemoPaymentCompleteRequest retryRequest = DemoPaymentCompleteRequest.builder()
                .paymentCode(retryPayment.getPaymentCode())
                .responseCode("00")
                .build();

        ResponseEntity<DemoPaymentCompleteResponse> retryResponse = demoPaymentController.completeDemoPayment(retryRequest);
        assertThat(retryResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(retryResponse.getBody()).isNotNull();
        assertThat(retryResponse.getBody().getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(retryPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

        verify(bookingService, times(1)).confirmPaidBooking(testBooking.getId(), retryPayment.getId());
    }
}

