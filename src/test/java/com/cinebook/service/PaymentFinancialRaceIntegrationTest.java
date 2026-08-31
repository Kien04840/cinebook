package com.cinebook.service;

import com.cinebook.config.VnPayConfig;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Payment;
import com.cinebook.entity.Seat;
import com.cinebook.entity.SeatHold;
import com.cinebook.entity.SeatType;
import com.cinebook.entity.Showtime;
import com.cinebook.entity.Ticket;
import com.cinebook.entity.User;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.enums.MovieStatus;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.enums.SeatStatus;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.mapper.AuditoriumMapper;
import com.cinebook.mapper.BookingMapper;
import com.cinebook.mapper.CinemaMapper;
import com.cinebook.mapper.GenreMapper;
import com.cinebook.mapper.MovieMapper;
import com.cinebook.mapper.SeatMapper;
import com.cinebook.mapper.ShowtimeMapper;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.PaymentRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.repository.SeatRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.repository.TicketRepository;
import com.cinebook.repository.UserRepository;
import com.cinebook.service.impl.BookingServiceImpl;
import com.cinebook.service.impl.PaymentServiceImpl;
import com.cinebook.task.BookingCleanupTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentFinancialRaceIntegrationTest {


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

    @Spy
    private GenreMapper genreMapper = new GenreMapper();

    @Spy
    private SeatMapper seatMapper = new SeatMapper();

    private MovieMapper movieMapper;
    private AuditoriumMapper auditoriumMapper;
    private CinemaMapper cinemaMapper;
    private ShowtimeMapper showtimeMapper;
    private BookingMapper bookingMapper;

    private BookingServiceImpl bookingService;
    private PaymentServiceImpl paymentService;
    private BookingCleanupTask bookingCleanupTask;

    private User testUser;
    private Booking testBooking;
    private Payment testPayment;
    private Seat testSeat;
    private SeatHold testHold;
    private Showtime testShowtime;

    private static final String TEST_TMN_CODE = "2QXUI4J4";

    @BeforeEach
    void setUp() {
        movieMapper = new MovieMapper(genreMapper);
        auditoriumMapper = new AuditoriumMapper(seatMapper);
        cinemaMapper = new CinemaMapper(auditoriumMapper);
        showtimeMapper = new ShowtimeMapper(movieMapper, cinemaMapper, auditoriumMapper);
        bookingMapper = new BookingMapper(showtimeMapper);

        bookingService = new BookingServiceImpl(
                bookingRepository,
                seatHoldRepository,
                ticketRepository,
                seatRepository,
                showtimeRepository,
                userRepository,
                paymentRepository,
                bookingMapper
        );

        paymentService = new PaymentServiceImpl(
                vnPayConfig,
                vnPayService,
                bookingService,
                bookingRepository,
                paymentRepository,
                seatHoldRepository,
                bookingMapper
        );

        bookingCleanupTask = new BookingCleanupTask(
                bookingRepository,
                seatHoldRepository
        );

        testUser = new User();
        testUser.setId("user-1");
        testUser.setEmail("customer@cinebook.com");

        Cinema cinema = new Cinema();
        cinema.setId("cinema-1");
        cinema.setStatus(CinemaStatus.ACTIVE);

        Auditorium auditorium = new Auditorium();
        auditorium.setId("aud-1");
        auditorium.setCinema(cinema);
        auditorium.setStatus(AuditoriumStatus.ACTIVE);

        Movie movie = new Movie();
        movie.setId("mov-1");
        movie.setStatus(MovieStatus.NOW_SHOWING);

        testShowtime = new Showtime();
        testShowtime.setId("showtime-1");
        testShowtime.setMovie(movie);
        testShowtime.setAuditorium(auditorium);
        testShowtime.setBasePrice(new BigDecimal("100000.00"));
        testShowtime.setStatus(ShowtimeStatus.SCHEDULED);

        SeatType standardType = new SeatType();
        standardType.setId("st-std");
        standardType.setName("STANDARD");
        standardType.setPriceModifier(BigDecimal.ZERO);

        testSeat = new Seat();
        testSeat.setId("seat-1");
        testSeat.setAuditorium(auditorium);
        testSeat.setRowLabel("A");
        testSeat.setSeatNumber((short) 1);
        testSeat.setSeatType(standardType);
        testSeat.setStatus(SeatStatus.ACTIVE);

        testBooking = new Booking();
        testBooking.setId("booking-race-1");
        testBooking.setBookingCode("CB-20260901-RACE01");
        testBooking.setUser(testUser);
        testBooking.setShowtime(testShowtime);
        testBooking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        testBooking.setTotalAmount(new BigDecimal("100000.00"));
        testBooking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired

        testHold = new SeatHold();
        testHold.setId(100L);
        testHold.setBooking(testBooking);
        testHold.setShowtime(testShowtime);
        testHold.setSeat(testSeat);
        testHold.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        testPayment = new Payment();
        testPayment.setId("payment-race-1");
        testPayment.setBooking(testBooking);
        testPayment.setPaymentMethod(PaymentMethod.VNPAY);
        testPayment.setPaymentCode("PAY-20260901-RACE01");
        testPayment.setAmount(new BigDecimal("100000.00"));
        testPayment.setPaymentStatus(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Scenario A: Hold expires -> Housekeeping marks EXPIRED -> Late VNPay IPN SUCCESS -> Payment is SUCCESS, Booking is EXPIRED, 0 Tickets, RspCode 00")
    void testScenarioA_HoldExpiredBeforeIpnSuccess() {
        // Step 1: Housekeeping runs and expires the booking
        when(bookingRepository.findExpiredBookings(any(), any()))
                .thenReturn(List.of(testBooking));
        when(bookingRepository.saveAll(any())).thenAnswer(i -> {
            testBooking.setBookingStatus(BookingStatus.EXPIRED);
            return i.getArgument(0);
        });

        bookingCleanupTask.cleanupExpiredBookingsAndHolds();

        assertThat(testBooking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);

        // Step 2: VNPay IPN arrives with SUCCESS
        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_TmnCode", TEST_TMN_CODE);
        ipnParams.put("vnp_TxnRef", "PAY-20260901-RACE01");
        ipnParams.put("vnp_Amount", "10000000");
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TransactionStatus", "00");
        ipnParams.put("vnp_TransactionNo", "14567890");
        ipnParams.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(any(), anyString())).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);
        when(paymentRepository.findByPaymentCode("PAY-20260901-RACE01")).thenReturn(Optional.of(testPayment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            testPayment.setPaymentStatus(p.getPaymentStatus());
            testPayment.setPaidAt(p.getPaidAt());
            testPayment.setGatewayTransactionId(p.getGatewayTransactionId());
            return testPayment;
        });

        when(bookingRepository.findById("booking-race-1")).thenReturn(Optional.of(testBooking));
        when(paymentRepository.findById("payment-race-1")).thenReturn(Optional.of(testPayment));

        IpnResponse ipnResponse = paymentService.processIpn(ipnParams);

        // INVARIANTS:
        // 1. IPN acknowledges receipt to VNPay
        assertThat(ipnResponse.getRspCode()).isEqualTo("00");
        assertThat(ipnResponse.getMessage()).isEqualTo("Confirm Success");

        // 2. Financial integrity: Payment record is SUCCESS with gateway transaction details
        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(testPayment.getGatewayTransactionId()).isEqualTo("14567890");
        assertThat(testPayment.getPaidAt()).isNotNull();

        // 3. Booking is NOT overwritten to PAID; remains EXPIRED
        assertThat(testBooking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);

        // 4. ZERO tickets created (prevent double-selling)
        assertThat(testBooking.getTickets()).isEmpty();
    }

    @Test
    @DisplayName("Scenario B: Concurrent Execution - Thread A (Housekeeping) vs Thread B (VNPay IPN) -> Invariant preserved under race")
    void testScenarioB_ConcurrentHousekeepingAndIpn() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ReentrantLock stateLock = new ReentrantLock();

        List<SeatHold> holds = new ArrayList<>(List.of(testHold));
        List<Ticket> savedTickets = new ArrayList<>();

        when(bookingRepository.findExpiredBookings(any(), any())).thenAnswer(i -> {
            stateLock.lock();
            try {
                if (testBooking.getBookingStatus() == BookingStatus.PENDING_PAYMENT) {
                    return List.of(testBooking);
                }
                return List.of();
            } finally {
                stateLock.unlock();
            }
        });

        when(bookingRepository.saveAll(any())).thenAnswer(i -> {
            stateLock.lock();
            try {
                testBooking.setBookingStatus(BookingStatus.EXPIRED);
                return i.getArgument(0);
            } finally {
                stateLock.unlock();
            }
        });

        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            stateLock.lock();
            try {
                Booking b = i.getArgument(0);
                testBooking.setBookingStatus(b.getBookingStatus());
                return testBooking;
            } finally {
                stateLock.unlock();
            }
        });

        org.mockito.Mockito.doAnswer(i -> {
            stateLock.lock();
            try {
                holds.clear();
                return null;
            } finally {
                stateLock.unlock();
            }
        }).when(seatHoldRepository).deleteByBookingId("booking-race-1");

        when(seatHoldRepository.findByBookingId("booking-race-1")).thenAnswer(i -> {
            stateLock.lock();
            try {
                return new ArrayList<>(holds);
            } finally {
                stateLock.unlock();
            }
        });

        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_TmnCode", TEST_TMN_CODE);
        ipnParams.put("vnp_TxnRef", "PAY-20260901-RACE01");
        ipnParams.put("vnp_Amount", "10000000");
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TransactionStatus", "00");
        ipnParams.put("vnp_TransactionNo", "14567890");
        ipnParams.put("vnp_SecureHash", "valid_hash");

        when(vnPayService.verifySignature(any(), anyString())).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn(TEST_TMN_CODE);

        when(paymentRepository.findByPaymentCode("PAY-20260901-RACE01")).thenReturn(Optional.of(testPayment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> {
            stateLock.lock();
            try {
                Payment p = i.getArgument(0);
                testPayment.setPaymentStatus(p.getPaymentStatus());
                testPayment.setPaidAt(p.getPaidAt());
                testPayment.setGatewayTransactionId(p.getGatewayTransactionId());
                return testPayment;
            } finally {
                stateLock.unlock();
            }
        });

        when(bookingRepository.findById("booking-race-1")).thenAnswer(i -> {
            stateLock.lock();
            try {
                return Optional.of(testBooking);
            } finally {
                stateLock.unlock();
            }
        });

        when(paymentRepository.findById("payment-race-1")).thenAnswer(i -> {
            stateLock.lock();
            try {
                return Optional.of(testPayment);
            } finally {
                stateLock.unlock();
            }
        });

        when(ticketRepository.saveAllAndFlush(any())).thenAnswer(i -> {
            stateLock.lock();
            try {
                List<Ticket> t = i.getArgument(0);
                savedTickets.addAll(t);
                return t;
            } finally {
                stateLock.unlock();
            }
        });

        Callable<Void> threadA_Cleanup = () -> {
            startLatch.await();
            bookingCleanupTask.cleanupExpiredBookingsAndHolds();
            doneLatch.countDown();
            return null;
        };

        Callable<IpnResponse> threadB_Ipn = () -> {
            startLatch.await();
            IpnResponse rsp = paymentService.processIpn(ipnParams);
            doneLatch.countDown();
            return rsp;
        };

        Future<Void> futureA = executor.submit(threadA_Cleanup);
        Future<IpnResponse> futureB = executor.submit(threadB_Ipn);

        startLatch.countDown(); // Start both threads
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        IpnResponse ipnRsp = futureB.get();
        assertThat(ipnRsp.getRspCode()).isEqualTo("00");

        // INVARIANTS MUST HOLD IN ALL OUTCOMES:
        // 1. Payment status must be SUCCESS
        assertThat(testPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // 2. Either Booking became PAID with valid tickets, OR Booking became EXPIRED with ZERO tickets
        if (testBooking.getBookingStatus() == BookingStatus.PAID) {
            assertThat(savedTickets).hasSize(1);
        } else {
            assertThat(testBooking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(savedTickets).isEmpty();
        }
    }
}

