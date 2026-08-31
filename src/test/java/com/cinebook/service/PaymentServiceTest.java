package com.cinebook.service;

import com.cinebook.config.VnPayConfig;
import com.cinebook.dto.request.InitiatePaymentRequest;
import com.cinebook.dto.response.InitiatePaymentResponse;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.dto.response.PaymentResultResponse;
import com.cinebook.dto.response.PaymentSummaryResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.entity.Seat;
import com.cinebook.entity.SeatHold;
import com.cinebook.entity.User;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ForbiddenException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.exception.UnauthorizedException;
import com.cinebook.mapper.BookingMapper;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.PaymentRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.PaymentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private VnPayConfig vnPayConfig;

    @Mock
    private VnPayService vnPayService;

    @Mock
    private BookingService bookingService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Mock
    private BookingMapper bookingMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;


    private User testCustomer;
    private User otherCustomer;
    private User testAdmin;
    private Booking testBooking;
    private Payment testPayment;
    private SeatHold testHold;

    private static final String TEST_TMN_CODE = "2QXUI4J4";

    @BeforeEach
    void setUp() {
        testCustomer = new User();
        testCustomer.setId("user-cust-1");
        testCustomer.setEmail("customer1@test.com");

        otherCustomer = new User();
        otherCustomer.setId("user-cust-2");
        otherCustomer.setEmail("customer2@test.com");

        testAdmin = new User();
        testAdmin.setId("user-admin-1");
        testAdmin.setEmail("admin@test.com");

        testBooking = new Booking();
        testBooking.setId("booking-1");
        testBooking.setBookingCode("CB-20260901-001");
        testBooking.setUser(testCustomer);
        testBooking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        testBooking.setTotalAmount(new BigDecimal("180000.00"));
        testBooking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));

        Seat seat = new Seat();
        seat.setId("seat-1");
        seat.setRowLabel("A");
        seat.setSeatNumber((short) 1);

        testHold = new SeatHold();
        testHold.setId(1L);
        testHold.setBooking(testBooking);
        testHold.setSeat(seat);
        testHold.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        testPayment = new Payment();
        testPayment.setId("payment-1");
        testPayment.setBooking(testBooking);
        testPayment.setPaymentMethod(PaymentMethod.VNPAY);
        testPayment.setPaymentCode("PAY-20260901-ABC12345");
        testPayment.setAmount(new BigDecimal("180000.00"));
        testPayment.setPaymentStatus(PaymentStatus.PENDING);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthentication(User user, String role) {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        SecurityContextHolder.setContext(securityContext);
    }



    // ==========================================
    // 1. INITIATE PAYMENT TESTS
    // ==========================================

    @Test
    @DisplayName("initiatePayment - Customer initiates payment for own booking successfully")
    void testInitiatePayment_Success() {
        mockAuthentication(testCustomer, "ROLE_CUSTOMER");

        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(testBooking));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(testHold));
        when(paymentRepository.existsByBookingIdAndPaymentStatus("booking-1", PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.existsByPaymentCode(anyString())).thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vnPayService.extractClientIp(httpRequest)).thenReturn("127.0.0.1");
        when(vnPayService.buildPaymentUrl(any(Payment.class), eq(testBooking), eq("127.0.0.1")))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=18000000...");

        InitiatePaymentResponse response = paymentService.initiatePayment("booking-1", request, httpRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("180000.00"));
        assertThat(response.getPaymentCode()).startsWith("PAY-");
        assertThat(response.getPaymentUrl()).contains("sandbox.vnpayment.vn");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("180000.00"));
    }

    @Test
    @DisplayName("initiatePayment - Other customer rejected with 403 Forbidden")
    void testInitiatePayment_OtherCustomer_ThrowsForbidden() {
        mockAuthentication(otherCustomer, "ROLE_CUSTOMER");

        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(testBooking));

        assertThatThrownBy(() -> paymentService.initiatePayment("booking-1", request, httpRequest))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Bạn không có quyền thao tác với thanh toán");
    }

    @Test
    @DisplayName("initiatePayment - Admin allowed to initiate payment")
    void testInitiatePayment_Admin_Success() {
        mockAuthentication(testAdmin, "ROLE_ADMIN");

        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(testBooking));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(testHold));
        when(paymentRepository.existsByBookingIdAndPaymentStatus("booking-1", PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vnPayService.extractClientIp(any())).thenReturn("127.0.0.1");
        when(vnPayService.buildPaymentUrl(any(Payment.class), eq(testBooking), any())).thenReturn("http://vnpay.url");

        InitiatePaymentResponse response = paymentService.initiatePayment("booking-1", request, httpRequest);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("initiatePayment - Booking not in PENDING_PAYMENT throws 400 Bad Request")
    void testInitiatePayment_BookingAlreadyPaid_ThrowsBadRequest() {
        mockAuthentication(testCustomer, "ROLE_CUSTOMER");
        testBooking.setBookingStatus(BookingStatus.PAID);

        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);
        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(testBooking));

        assertThatThrownBy(() -> paymentService.initiatePayment("booking-1", request, new MockHttpServletRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thể tạo thanh toán mới");
    }

    @Test
    @DisplayName("initiatePayment - Booking seat hold expired throws 400 Bad Request")
    void testInitiatePayment_HoldExpired_ThrowsBadRequest() {
        mockAuthentication(testCustomer, "ROLE_CUSTOMER");
        testBooking.setHoldExpiresAt(LocalDateTime.now().minusSeconds(10));

        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);
        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(testBooking));

        assertThatThrownBy(() -> paymentService.initiatePayment("booking-1", request, new MockHttpServletRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Đơn đặt vé đã hết hạn giữ chỗ");
    }

    @Test
    @DisplayName("initiatePayment - Existing PENDING payment throws 409 Conflict")
    void testInitiatePayment_ExistingPendingPayment_ThrowsConflict() {
        mockAuthentication(testCustomer, "ROLE_CUSTOMER");

        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);
        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(testBooking));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(testHold));
        when(paymentRepository.existsByBookingIdAndPaymentStatus("booking-1", PaymentStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.initiatePayment("booking-1", request, new MockHttpServletRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Đơn đặt vé đang có một phiên thanh toán đang chờ xử lý");
    }

    @Test
    @DisplayName("initiatePayment - Previous payment FAILED allows retry")
    void testInitiatePayment_PreviousPaymentFailed_AllowsRetry() {
        mockAuthentication(testCustomer, "ROLE_CUSTOMER");

        // Previous payment was FAILED
        when(bookingRepository.findByIdWithLock("booking-1")).thenReturn(Optional.of(testBooking));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(testHold));
        when(paymentRepository.existsByBookingIdAndPaymentStatus("booking-1", PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vnPayService.extractClientIp(any())).thenReturn("127.0.0.1");
        when(vnPayService.buildPaymentUrl(any(Payment.class), eq(testBooking), any())).thenReturn("http://vnpay.url");

        InitiatePaymentResponse response = paymentService.initiatePayment("booking-1", new InitiatePaymentRequest(PaymentMethod.VNPAY), new MockHttpServletRequest());
        assertThat(response).isNotNull();
    }


    @Test
    @DisplayName("initiatePayment - Unsupported payment method throws 400 Bad Request")
    void testInitiatePayment_UnsupportedMethod_ThrowsBadRequest() {
        mockAuthentication(testCustomer, "ROLE_CUSTOMER");

        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.MOMO);

        assertThatThrownBy(() -> paymentService.initiatePayment("booking-1", request, new MockHttpServletRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("V1 chỉ hỗ trợ VNPAY");
    }


    // ==========================================
    // 2. IPN PROCESSING TESTS
    // ==========================================

    @Test
    @DisplayName("processIpn - Valid SUCCESS (00) confirms booking and marks payment SUCCESS")
    void testProcessIpn_Success() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_Amount", "18000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(params, "valid_hash")).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);
        when(paymentRepository.findByPaymentCode("PAY-20260901-ABC12345")).thenReturn(Optional.of(testPayment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("00");
        assertThat(response.getMessage()).isEqualTo("Confirm Success");
        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(testPayment.getGatewayTransactionId()).isEqualTo("14567890");

        verify(bookingService).confirmPaidBooking("booking-1", "payment-1");
    }

    @Test
    @DisplayName("processIpn - User Cancel (24) marks payment CANCELLED and does not confirm booking")
    void testProcessIpn_UserCancel_MarksCancelled() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_Amount", "18000000");
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(params, "valid_hash")).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);
        when(paymentRepository.findByPaymentCode("PAY-20260901-ABC12345")).thenReturn(Optional.of(testPayment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("00");
        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(bookingService, never()).confirmPaidBooking(anyString(), anyString());
    }

    @Test
    @DisplayName("processIpn - Gateway Error (51) marks payment FAILED and does not confirm booking")
    void testProcessIpn_GatewayError_MarksFailed() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_Amount", "18000000");
        params.put("vnp_ResponseCode", "51"); // Insufficient balance
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(params, "valid_hash")).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);
        when(paymentRepository.findByPaymentCode("PAY-20260901-ABC12345")).thenReturn(Optional.of(testPayment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("00");
        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(bookingService, never()).confirmPaidBooking(anyString(), anyString());
    }

    @Test
    @DisplayName("processIpn - Invalid Checksum returns RspCode 97")
    void testProcessIpn_InvalidChecksum_ReturnsRspCode97() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_SecureHash", "bad_hash");

        when(vnPayService.verifySignature(params, "bad_hash")).thenReturn(false);

        IpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("97");
        assertThat(response.getMessage()).isEqualTo("Invalid Checksum");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("processIpn - Invalid TMN Code returns RspCode 01")
    void testProcessIpn_InvalidTmnCode_ReturnsRspCode01() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "WRONG_TMN");
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(params, "valid_hash")).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);

        IpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("01");
        assertThat(response.getMessage()).isEqualTo("Order not Found");
    }

    @Test
    @DisplayName("processIpn - Payment not found returns RspCode 01")
    void testProcessIpn_PaymentNotFound_ReturnsRspCode01() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-NONEXISTENT");
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(params, "valid_hash")).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);
        when(paymentRepository.findByPaymentCode("PAY-NONEXISTENT")).thenReturn(Optional.empty());

        IpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("01");
        assertThat(response.getMessage()).isEqualTo("Order not Found");
    }

    @Test
    @DisplayName("processIpn - Amount mismatch returns RspCode 04")
    void testProcessIpn_AmountMismatch_ReturnsRspCode04() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_Amount", "99999900"); // Mismatch amount
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(params, "valid_hash")).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);
        when(paymentRepository.findByPaymentCode("PAY-20260901-ABC12345")).thenReturn(Optional.of(testPayment));

        IpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("04");
        assertThat(response.getMessage()).isEqualTo("Invalid Amount");
    }

    @Test
    @DisplayName("processIpn - Duplicate IPN on already confirmed payment returns RspCode 02 (Idempotent)")
    void testProcessIpn_DuplicateCall_ReturnsRspCode02() {
        testPayment.setPaymentStatus(PaymentStatus.SUCCESS);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_Amount", "18000000");
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(params, "valid_hash")).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);
        when(paymentRepository.findByPaymentCode("PAY-20260901-ABC12345")).thenReturn(Optional.of(testPayment));

        IpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("02");
        assertThat(response.getMessage()).isEqualTo("Order already confirmed");
        verify(bookingService, never()).confirmPaidBooking(anyString(), anyString());
    }

    @Test
    @DisplayName("processIpn - Payment SUCCESS but Booking EXPIRED race condition is handled safely")
    void testProcessIpn_ExpiredBooking_FinancialEdgeCaseHandledSafely() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_Amount", "18000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(params, "valid_hash")).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);
        when(paymentRepository.findByPaymentCode("PAY-20260901-ABC12345")).thenReturn(Optional.of(testPayment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // simulate confirmPaidBooking failing due to expired hold
        when(bookingService.confirmPaidBooking("booking-1", "payment-1"))
                .thenThrow(new BadRequestException("Đơn đặt vé đã hết hạn giữ chỗ."));

        IpnResponse response = paymentService.processIpn(params);

        // Payment status stays SUCCESS for financial audit/reconciliation
        assertThat(response.getRspCode()).isEqualTo("00");
        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    // ==========================================
    // 3. RETURN ENDPOINT TESTS
    // ==========================================

    @Test
    @DisplayName("processReturn - Valid signature returns read-only result DTO")
    void testProcessReturn_Success() {
        testPayment.setPaymentStatus(PaymentStatus.SUCCESS);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(params, "valid_hash")).thenReturn(true);
        when(paymentRepository.findByPaymentCode("PAY-20260901-ABC12345")).thenReturn(Optional.of(testPayment));

        PaymentResultResponse response = paymentService.processReturn(params);

        assertThat(response).isNotNull();
        assertThat(response.getPaymentCode()).isEqualTo("PAY-20260901-ABC12345");
        assertThat(response.getBookingCode()).isEqualTo("CB-20260901-001");
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("180000.00"));
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getMessage()).isEqualTo("Giao dịch thanh toán thành công.");

        // Read-only invariant: Never calls confirmPaidBooking or mutates any entities
        verify(bookingService, never()).confirmPaidBooking(anyString(), anyString());
        verify(paymentRepository, never()).save(any());
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(bookingRepository, never()).save(any());
        verify(seatHoldRepository, never()).deleteByBookingId(any());
    }


    @Test
    @DisplayName("processReturn - Invalid signature throws 400 Bad Request")
    void testProcessReturn_InvalidSignature_ThrowsBadRequest() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_SecureHash", "bad_hash");

        when(vnPayService.verifySignature(params, "bad_hash")).thenReturn(false);

        assertThatThrownBy(() -> paymentService.processReturn(params))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chữ ký phản hồi không hợp lệ");
    }

    // ==========================================
    // 4. GET PAYMENT DETAIL TESTS
    // ==========================================

    @Test
    @DisplayName("getPaymentDetail - Customer accesses own payment successfully")
    void testGetPaymentDetail_Owner_Success() {
        mockAuthentication(testCustomer, "ROLE_CUSTOMER");

        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(testPayment));
        PaymentSummaryResponse summary = PaymentSummaryResponse.builder()
                .id("payment-1")
                .paymentCode("PAY-20260901-ABC12345")
                .amount(new BigDecimal("180000.00"))
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        when(bookingMapper.toPaymentSummaryResponse(testPayment)).thenReturn(summary);

        PaymentSummaryResponse result = paymentService.getPaymentDetail("payment-1");
        assertThat(result).isNotNull();
        assertThat(result.getPaymentCode()).isEqualTo("PAY-20260901-ABC12345");
    }

    @Test
    @DisplayName("getPaymentDetail - Other customer throws 403 Forbidden")
    void testGetPaymentDetail_OtherCustomer_ThrowsForbidden() {
        mockAuthentication(otherCustomer, "ROLE_CUSTOMER");

        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(testPayment));

        assertThatThrownBy(() -> paymentService.getPaymentDetail("payment-1"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Bạn không có quyền thao tác với thanh toán");
    }
}

