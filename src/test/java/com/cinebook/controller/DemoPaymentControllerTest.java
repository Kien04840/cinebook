package com.cinebook.controller;

import com.cinebook.config.VnPayConfig;
import com.cinebook.dto.request.DemoPaymentCompleteRequest;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.entity.*;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ForbiddenException;
import com.cinebook.repository.PaymentRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.PaymentService;
import com.cinebook.service.VnPayService;
import com.cinebook.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoPaymentControllerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private VnPayService vnPayService;

    @Mock
    private VnPayConfig vnPayConfig;

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @InjectMocks
    private DemoPaymentController demoPaymentController;

    private MockedStatic<SecurityUtils> mockedSecurityUtils;
    private UserDetailsImpl customerUser;
    private UserDetailsImpl otherCustomerUser;
    private User user;
    private Booking booking;
    private Payment payment;

    @BeforeEach
    void setUp() {
        mockedSecurityUtils = mockStatic(SecurityUtils.class);

        customerUser = UserDetailsImpl.builder()
                .id("user-1")
                .email("customer@test.com")
                .fullName("Customer Test")
                .password("hash")
                .status(com.cinebook.enums.UserStatus.ACTIVE)
                .isDeleted(false)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();

        otherCustomerUser = UserDetailsImpl.builder()
                .id("user-2")
                .email("other@test.com")
                .fullName("Other Customer")
                .password("hash")
                .status(com.cinebook.enums.UserStatus.ACTIVE)
                .isDeleted(false)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();

        user = new User();
        user.setId("user-1");
        user.setEmail("customer@test.com");
        user.setFullName("Customer Test");

        booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-20260906-001");
        booking.setUser(user);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("150000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));

        payment = new Payment();
        payment.setId("payment-1");
        payment.setPaymentCode("PAY-20260906-XYZ");
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("150000.00"));
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentStatus(PaymentStatus.PENDING);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("Complete Demo Payment - Success (00) triggers IPN and returns valid redirect URL")
    void testCompleteDemoPayment_Success() {
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails).thenReturn(Optional.of(customerUser));

        when(paymentRepository.findByPaymentCode("PAY-20260906-XYZ")).thenReturn(Optional.of(payment));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(new SeatHold()));
        when(vnPayConfig.getVersion()).thenReturn("2.1.0");
        when(vnPayConfig.getCommand()).thenReturn("pay");
        when(vnPayConfig.getTmnCode()).thenReturn("MOCK_TMN");
        when(vnPayConfig.getHashSecret()).thenReturn("MOCK_SECRET_KEY");
        when(vnPayService.calculateHmacSha512(anyMap(), anyString())).thenReturn("mock-secure-hash-512");
        when(paymentService.processIpn(anyMap())).thenReturn(new IpnResponse("00", "Confirm Success"));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode("PAY-20260906-XYZ")
                .responseCode("00")
                .build();

        var response = demoPaymentController.completeDemoPayment(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("00");
        assertThat(response.getBody().getRedirectUrl()).contains("/payment/result?");
        assertThat(response.getBody().getRedirectUrl()).contains("vnp_SecureHash=mock-secure-hash-512");

        verify(paymentService).processIpn(argThat(params ->
                "00".equals(params.get("vnp_ResponseCode")) &&
                "15000000".equals(params.get("vnp_Amount")) &&
                "PAY-20260906-XYZ".equals(params.get("vnp_TxnRef")) &&
                "mock-secure-hash-512".equals(params.get("vnp_SecureHash"))
        ));
    }

    @Test
    @DisplayName("Complete Demo Payment - Cancelled (24) triggers IPN with responseCode 24")
    void testCompleteDemoPayment_Cancelled() {
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails).thenReturn(Optional.of(customerUser));

        when(paymentRepository.findByPaymentCode("PAY-20260906-XYZ")).thenReturn(Optional.of(payment));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(new SeatHold()));
        when(vnPayConfig.getVersion()).thenReturn("2.1.0");
        when(vnPayConfig.getCommand()).thenReturn("pay");
        when(vnPayConfig.getTmnCode()).thenReturn("MOCK_TMN");
        when(vnPayConfig.getHashSecret()).thenReturn("MOCK_SECRET_KEY");
        when(vnPayService.calculateHmacSha512(anyMap(), anyString())).thenReturn("mock-secure-hash-512");
        when(paymentService.processIpn(anyMap())).thenReturn(new IpnResponse("00", "Confirm Success"));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode("PAY-20260906-XYZ")
                .responseCode("24")
                .build();

        var response = demoPaymentController.completeDemoPayment(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("24");
        assertThat(response.getBody().getRedirectUrl()).contains("vnp_ResponseCode=24");

        verify(paymentService).processIpn(argThat(params -> "24".equals(params.get("vnp_ResponseCode"))));
    }

    @Test
    @DisplayName("Complete Demo Payment - Failed (07) triggers IPN with responseCode 07")
    void testCompleteDemoPayment_Failed() {
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails).thenReturn(Optional.of(customerUser));

        when(paymentRepository.findByPaymentCode("PAY-20260906-XYZ")).thenReturn(Optional.of(payment));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(new SeatHold()));
        when(vnPayConfig.getVersion()).thenReturn("2.1.0");
        when(vnPayConfig.getCommand()).thenReturn("pay");
        when(vnPayConfig.getTmnCode()).thenReturn("MOCK_TMN");
        when(vnPayConfig.getHashSecret()).thenReturn("MOCK_SECRET_KEY");
        when(vnPayService.calculateHmacSha512(anyMap(), anyString())).thenReturn("mock-secure-hash-512");
        when(paymentService.processIpn(anyMap())).thenReturn(new IpnResponse("00", "Confirm Success"));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode("PAY-20260906-XYZ")
                .responseCode("07")
                .build();

        var response = demoPaymentController.completeDemoPayment(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("07");
        assertThat(response.getBody().getRedirectUrl()).contains("vnp_ResponseCode=07");

        verify(paymentService).processIpn(argThat(params -> "07".equals(params.get("vnp_ResponseCode"))));
    }

    @Test
    @DisplayName("Complete Demo Payment - Unauthorized user attempting to complete another user's payment throws 403 Forbidden")
    void testCompleteDemoPayment_Unauthorized_Throws403() {
        // User 2 logs in and tries to pay User 1's booking
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails).thenReturn(Optional.of(otherCustomerUser));
        when(paymentRepository.findByPaymentCode("PAY-20260906-XYZ")).thenReturn(Optional.of(payment));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode("PAY-20260906-XYZ")
                .responseCode("00")
                .build();

        assertThatThrownBy(() -> demoPaymentController.completeDemoPayment(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Bạn không có quyền thao tác với thanh toán của đơn đặt vé này.");

        verify(paymentService, never()).processIpn(anyMap());
    }

    @Test
    @DisplayName("Complete Demo Payment - Idempotency: already SUCCESS returns redirect URL without calling IPN again")
    void testCompleteDemoPayment_AlreadySuccess_Idempotent() {
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails).thenReturn(Optional.of(customerUser));

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByPaymentCode("PAY-20260906-XYZ")).thenReturn(Optional.of(payment));
        when(vnPayConfig.getVersion()).thenReturn("2.1.0");
        when(vnPayConfig.getCommand()).thenReturn("pay");
        when(vnPayConfig.getTmnCode()).thenReturn("MOCK_TMN");
        when(vnPayConfig.getHashSecret()).thenReturn("MOCK_SECRET_KEY");
        when(vnPayService.calculateHmacSha512(anyMap(), anyString())).thenReturn("mock-secure-hash-512");

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode("PAY-20260906-XYZ")
                .responseCode("00")
                .build();

        var response = demoPaymentController.completeDemoPayment(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getBody().getMessage()).contains("đã được xác nhận thành công trước đó");

        // processIpn must NOT be called again
        verify(paymentService, never()).processIpn(anyMap());
    }

    @Test
    @DisplayName("Complete Demo Payment - Expired booking hold throws BadRequestException")
    void testCompleteDemoPayment_ExpiredHold_ThrowsBadRequest() {
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails).thenReturn(Optional.of(customerUser));

        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired
        when(paymentRepository.findByPaymentCode("PAY-20260906-XYZ")).thenReturn(Optional.of(payment));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode("PAY-20260906-XYZ")
                .responseCode("00")
                .build();

        assertThatThrownBy(() -> demoPaymentController.completeDemoPayment(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Đơn đặt vé đã hết hạn giữ chỗ.");

        verify(paymentService, never()).processIpn(anyMap());
    }

    @Test
    @DisplayName("Complete Demo Payment - Missing seat hold throws BadRequestException")
    void testCompleteDemoPayment_MissingSeatHold_ThrowsBadRequest() {
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails).thenReturn(Optional.of(customerUser));

        when(paymentRepository.findByPaymentCode("PAY-20260906-XYZ")).thenReturn(Optional.of(payment));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList()); // Empty holds

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode("PAY-20260906-XYZ")
                .responseCode("00")
                .build();

        assertThatThrownBy(() -> demoPaymentController.completeDemoPayment(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không tìm thấy thông tin giữ chỗ");

        verify(paymentService, never()).processIpn(anyMap());
    }

    @Test
    @DisplayName("Complete Demo Payment - Payment code not found throws ResourceNotFoundException")
    void testCompleteDemoPayment_PaymentNotFound_Throws404() {
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails).thenReturn(Optional.of(customerUser));
        when(paymentRepository.findByPaymentCode("PAY-UNKNOWN")).thenReturn(Optional.empty());

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode("PAY-UNKNOWN")
                .responseCode("00")
                .build();

        assertThatThrownBy(() -> demoPaymentController.completeDemoPayment(request))
                .isInstanceOf(com.cinebook.exception.ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy thông tin thanh toán");

        verify(paymentService, never()).processIpn(anyMap());
    }

    @Test
    @DisplayName("Complete Demo Payment - Terminal FAILED payment cannot be completed without new session")
    void testCompleteDemoPayment_TerminalFailed_ThrowsBadRequest() {
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserDetails).thenReturn(Optional.of(customerUser));
        payment.setPaymentStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByPaymentCode("PAY-20260906-XYZ")).thenReturn(Optional.of(payment));

        DemoPaymentCompleteRequest request = DemoPaymentCompleteRequest.builder()
                .paymentCode("PAY-20260906-XYZ")
                .responseCode("00")
                .build();

        assertThatThrownBy(() -> demoPaymentController.completeDemoPayment(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thể tiếp tục xử lý");

        verify(paymentService, never()).processIpn(anyMap());
    }
}

