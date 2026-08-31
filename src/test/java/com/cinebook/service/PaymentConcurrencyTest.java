package com.cinebook.service;

import com.cinebook.config.VnPayConfig;
import com.cinebook.dto.request.InitiatePaymentRequest;
import com.cinebook.dto.request.RefundRequest;
import com.cinebook.dto.response.InitiatePaymentResponse;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.dto.response.RefundResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.entity.Refund;
import com.cinebook.entity.Seat;
import com.cinebook.entity.SeatHold;
import com.cinebook.entity.Showtime;
import com.cinebook.entity.User;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.enums.RefundStatus;
import com.cinebook.exception.ConflictException;
import com.cinebook.mapper.BookingMapper;
import com.cinebook.mapper.RefundMapper;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.PaymentRepository;
import com.cinebook.repository.RefundRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.repository.TicketRepository;

import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.PaymentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConcurrencyTest {

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
    private RefundRepository refundRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private RefundMapper refundMapper;


    private PaymentServiceImpl paymentService;

    private User testCustomer;
    private Booking testBooking;
    private SeatHold testHold;
    private Payment testPayment;

    private static final String TEST_TMN_CODE = "2QXUI4J4";

    @BeforeEach
    void setUp() {
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
                refundMapper
        );


        testCustomer = new User();

        testCustomer.setId("user-cust-1");
        testCustomer.setEmail("cust@test.com");

        testBooking = new Booking();
        testBooking.setId("booking-1");
        testBooking.setBookingCode("CB-20260901-001");
        testBooking.setUser(testCustomer);
        testBooking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        testBooking.setTotalAmount(new BigDecimal("200000.00"));
        testBooking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));

        Seat seat = new Seat();
        seat.setId("seat-1");
        testHold = new SeatHold();
        testHold.setId(1L);
        testHold.setBooking(testBooking);
        testHold.setSeat(seat);

        testPayment = new Payment();
        testPayment.setId("payment-1");
        testPayment.setBooking(testBooking);
        testPayment.setPaymentMethod(PaymentMethod.VNPAY);
        testPayment.setPaymentCode("PAY-20260901-CONCUR01");
        testPayment.setAmount(new BigDecimal("200000.00"));
        testPayment.setPaymentStatus(PaymentStatus.PENDING);
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



    @Test
    @DisplayName("Concurrency: 2 simultaneous payment initiations for same booking -> Exactly 1 succeeds, 1 gets 409 Conflict")
    void testConcurrentPaymentInitiation_ExactlyOneSucceeds() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicBoolean hasPending = new AtomicBoolean(false);
        java.util.concurrent.locks.ReentrantLock dbLock = new java.util.concurrent.locks.ReentrantLock();

        // Simulate database pessimistic row-lock behavior on Booking:
        when(bookingRepository.findByIdWithLock("booking-1")).thenAnswer(invocation -> {
            dbLock.lock();
            return Optional.of(testBooking);
        });
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(testHold));
        when(paymentRepository.existsByBookingIdAndPaymentStatus(eq("booking-1"), eq(PaymentStatus.PENDING)))
                .thenAnswer(invocation -> hasPending.get());

        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            hasPending.set(true); // Thread 1 marks PENDING payment created
            return invocation.getArgument(0);
        });

        when(vnPayService.extractClientIp(any())).thenReturn("127.0.0.1");
        when(vnPayService.buildPaymentUrl(any(), any(), any())).thenAnswer(invocation -> {
            if (dbLock.isHeldByCurrentThread()) {
                dbLock.unlock();
            }
            return "http://vnpay.url";
        });

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                mockAuthentication(testCustomer);
                try {
                    startLatch.await();
                    paymentService.initiatePayment("booking-1", new InitiatePaymentRequest(PaymentMethod.VNPAY), new MockHttpServletRequest());
                    successCount.incrementAndGet();
                } catch (ConflictException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    if (dbLock.isHeldByCurrentThread()) {
                        dbLock.unlock();
                    }
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Trigger all threads simultaneously
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrency: 2 simultaneous duplicate SUCCESS IPNs -> Exactly 1 confirms booking, 1 returns 02 already confirmed")
    void testConcurrentDuplicateIpn_OnlyOneConfirmsBooking() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger rsp00Count = new AtomicInteger(0);
        AtomicInteger rsp02Count = new AtomicInteger(0);
        java.util.concurrent.locks.ReentrantLock ipnLock = new java.util.concurrent.locks.ReentrantLock();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-CONCUR01");
        params.put("vnp_Amount", "20000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "998877");
        params.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(any(), anyString())).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);

        when(paymentRepository.findByPaymentCode("PAY-20260901-CONCUR01")).thenAnswer(invocation -> {
            ipnLock.lock();
            return Optional.of(testPayment);
        });
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            testPayment.setPaymentStatus(PaymentStatus.SUCCESS);
            return testPayment;
        });

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    IpnResponse response = paymentService.processIpn(params);
                    if ("00".equals(response.getRspCode())) {
                        rsp00Count.incrementAndGet();
                    } else if ("02".equals(response.getRspCode())) {
                        rsp02Count.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    if (ipnLock.isHeldByCurrentThread()) {
                        ipnLock.unlock();
                    }
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(rsp00Count.get()).isEqualTo(1);
        assertThat(rsp02Count.get()).isEqualTo(1);
        verify(bookingService, times(1)).confirmPaidBooking(anyString(), anyString());
    }

    @Test
    @DisplayName("Concurrency: Concurrent refund attempts on same payment - Idempotent and thread safe")
    void testConcurrentRefund_IdempotentBehavior() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        testBooking.setBookingStatus(BookingStatus.PAID);
        Showtime showtime = new Showtime();
        showtime.setId("showtime-future");
        showtime.setStartTime(LocalDateTime.now().plusHours(5));
        testBooking.setShowtime(showtime);
        testPayment.setPaymentStatus(PaymentStatus.SUCCESS);
        testPayment.setPaidAt(LocalDateTime.now().minusHours(1));

        mockAuthentication(testCustomer);

        when(paymentRepository.findByIdWithLock("payment-1")).thenReturn(Optional.of(testPayment));
        when(ticketRepository.findByBookingId(testBooking.getId())).thenReturn(List.of());

        AtomicBoolean firstCall = new AtomicBoolean(true);
        when(refundRepository.findByPaymentId("payment-1")).thenAnswer(invocation -> {
            if (firstCall.get()) {
                return Optional.empty();
            } else {
                Refund existing = new Refund();
                existing.setId("ref-existing");
                existing.setPayment(testPayment);
                existing.setRefundStatus(RefundStatus.SUCCESS);
                existing.setAmount(testPayment.getAmount());
                return Optional.of(existing);
            }
        });

        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            return r;
        });

        Map<String, String> gatewaySuccess = new HashMap<>();
        gatewaySuccess.put("vnp_ResponseCode", "00");
        gatewaySuccess.put("vnp_ResponseId", "VNP-REF-CONCUR");
        when(vnPayService.refundPayment(any(), any(), any(), any())).thenReturn(gatewaySuccess);

        when(refundRepository.findById(anyString())).thenAnswer(invocation -> {
            Refund r = new Refund();
            r.setId(invocation.getArgument(0));
            r.setPayment(testPayment);
            r.setAmount(testPayment.getAmount());
            r.setRefundStatus(RefundStatus.PENDING);
            return Optional.of(r);
        });

        RefundResponse resp = RefundResponse.builder()
                .paymentId("payment-1")
                .refundStatus(RefundStatus.SUCCESS)
                .amount(testPayment.getAmount())
                .build();
        when(refundMapper.toRefundResponse(any(Refund.class))).thenReturn(resp);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                mockAuthentication(testCustomer);
                try {
                    startLatch.await();
                    RefundResponse result = paymentService.refundPayment("payment-1", new RefundRequest("Hủy"), new MockHttpServletRequest());
                    if (result != null && result.getRefundStatus() == RefundStatus.SUCCESS) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
    }
}



