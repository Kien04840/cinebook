package com.cinebook.service;

import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.BookingDetailResponse;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.ConflictException;
import com.cinebook.mapper.*;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingConcurrencyTest {

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

    private User userA;
    private User userB;
    private Showtime sampleShowtime;
    private Seat seatA1;
    private Seat seatA2;
    private Seat seatA3;

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

        userA = new User();
        userA.setId("user-a");
        userA.setEmail("usera@example.com");

        userB = new User();
        userB.setId("user-b");
        userB.setEmail("userb@example.com");

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
        movie.setDurationMinutes((short) 120);

        sampleShowtime = new Showtime();
        sampleShowtime.setId("showtime-1");
        sampleShowtime.setMovie(movie);
        sampleShowtime.setAuditorium(auditorium);
        sampleShowtime.setStartTime(LocalDateTime.now().plusDays(1));
        sampleShowtime.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        sampleShowtime.setBasePrice(new BigDecimal("100000.00"));
        sampleShowtime.setStatus(ShowtimeStatus.SCHEDULED);

        SeatType standardType = new SeatType();
        standardType.setId("st-std");
        standardType.setName("STANDARD");
        standardType.setPriceModifier(BigDecimal.ZERO);

        seatA1 = new Seat();
        seatA1.setId("seat-a1");
        seatA1.setAuditorium(auditorium);
        seatA1.setRowLabel("A");
        seatA1.setSeatNumber((short) 1);
        seatA1.setSeatType(standardType);
        seatA1.setStatus(SeatStatus.ACTIVE);

        seatA2 = new Seat();
        seatA2.setId("seat-a2");
        seatA2.setAuditorium(auditorium);
        seatA2.setRowLabel("A");
        seatA2.setSeatNumber((short) 2);
        seatA2.setSeatType(standardType);
        seatA2.setStatus(SeatStatus.ACTIVE);

        seatA3 = new Seat();
        seatA3.setId("seat-a3");
        seatA3.setAuditorium(auditorium);
        seatA3.setRowLabel("A");
        seatA3.setSeatNumber((short) 3);
        seatA3.setSeatType(standardType);
        seatA3.setStatus(SeatStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentBooking_SameSeat_ExactlyOneSucceedsAndOneConflicts() throws Exception {
        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seatA1));
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());

        when(userRepository.findById("user-a")).thenReturn(Optional.of(userA));
        when(userRepository.findById("user-b")).thenReturn(Optional.of(userB));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(UUID.randomUUID().toString());
            b.setCreatedAt(LocalDateTime.now());
            return b;
        });

        // Simulate database unique constraint uk_seat_holds_showtime_seat on second concurrent save
        AtomicInteger holdSaveCount = new AtomicInteger(0);
        when(seatHoldRepository.saveAllAndFlush(anyList())).thenAnswer(i -> {
            int count = holdSaveCount.incrementAndGet();
            if (count > 1) {
                throw new DataIntegrityViolationException("Duplicate entry 'showtime-1-seat-a1' for key 'uk_seat_holds_showtime_seat'");
            }
            return i.getArgument(0);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<BookingDetailResponse> taskUserA = () -> {
            latch.await();
            UserDetailsImpl userDetails = UserDetailsImpl.builder()
                    .id("user-a")
                    .email("usera@example.com")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );
            return bookingService.createBooking(CreateBookingRequest.builder()
                    .showtimeId("showtime-1")
                    .seatIds(List.of("seat-a1"))
                    .build());
        };

        Callable<BookingDetailResponse> taskUserB = () -> {
            latch.await();
            UserDetailsImpl userDetails = UserDetailsImpl.builder()
                    .id("user-b")
                    .email("userb@example.com")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );
            return bookingService.createBooking(CreateBookingRequest.builder()
                    .showtimeId("showtime-1")
                    .seatIds(List.of("seat-a1"))
                    .build());
        };

        Future<BookingDetailResponse> futureA = executor.submit(taskUserA);
        Future<BookingDetailResponse> futureB = executor.submit(taskUserB);

        latch.countDown(); // Trigger both threads simultaneously

        int successCount = 0;
        int conflictCount = 0;

        try {
            futureA.get();
            successCount++;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ConflictException) {
                conflictCount++;
            }
        }

        try {
            futureB.get();
            successCount++;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ConflictException) {
                conflictCount++;
            }
        }

        executor.shutdown();

        assertEquals(1, successCount, "Exactly one concurrent booking must succeed");
        assertEquals(1, conflictCount, "Exactly one concurrent booking must fail with 409 Conflict");
    }

    @Test
    void concurrentBooking_OverlappingSeats_WinnerTakesAllAndLoserRollsBack() throws Exception {
        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenAnswer(i -> {
            Collection<String> ids = i.getArgument(0);
            List<Seat> matched = new ArrayList<>();
            for (String id : ids) {
                if (id.equals("seat-a1")) matched.add(seatA1);
                if (id.equals("seat-a2")) matched.add(seatA2);
                if (id.equals("seat-a3")) matched.add(seatA3);
            }
            return matched;
        });
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());

        when(userRepository.findById("user-a")).thenReturn(Optional.of(userA));
        when(userRepository.findById("user-b")).thenReturn(Optional.of(userB));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(UUID.randomUUID().toString());
            b.setCreatedAt(LocalDateTime.now());
            return b;
        });

        // Track seats held in DB simulation
        Set<String> simulatedHeldSeats = Collections.synchronizedSet(new HashSet<>());
        when(seatHoldRepository.saveAllAndFlush(anyList())).thenAnswer(i -> {
            List<SeatHold> holds = i.getArgument(0);
            synchronized (simulatedHeldSeats) {
                for (SeatHold h : holds) {
                    if (simulatedHeldSeats.contains(h.getSeat().getId())) {
                        throw new DataIntegrityViolationException("Unique constraint violation for seat " + h.getSeat().getId());
                    }
                }
                for (SeatHold h : holds) {
                    simulatedHeldSeats.add(h.getSeat().getId());
                }
            }
            return holds;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<BookingDetailResponse> taskUserA = () -> {
            latch.await();
            UserDetailsImpl userDetails = UserDetailsImpl.builder()
                    .id("user-a")
                    .email("usera@example.com")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );
            return bookingService.createBooking(CreateBookingRequest.builder()
                    .showtimeId("showtime-1")
                    .seatIds(List.of("seat-a1", "seat-a2"))
                    .build());
        };

        Callable<BookingDetailResponse> taskUserB = () -> {
            latch.await();
            UserDetailsImpl userDetails = UserDetailsImpl.builder()
                    .id("user-b")
                    .email("userb@example.com")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );
            return bookingService.createBooking(CreateBookingRequest.builder()
                    .showtimeId("showtime-1")
                    .seatIds(List.of("seat-a2", "seat-a3"))
                    .build());
        };

        Future<BookingDetailResponse> futureA = executor.submit(taskUserA);
        Future<BookingDetailResponse> futureB = executor.submit(taskUserB);

        latch.countDown();

        int successCount = 0;
        int conflictCount = 0;

        try {
            futureA.get();
            successCount++;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ConflictException) {
                conflictCount++;
            }
        }

        try {
            futureB.get();
            successCount++;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ConflictException) {
                conflictCount++;
            }
        }

        executor.shutdown();

        assertEquals(1, successCount, "Exactly one booking with overlapping seats must succeed");
        assertEquals(1, conflictCount, "Conflicting booking must fully fail and roll back");
        assertEquals(2, simulatedHeldSeats.size(), "Only the winner's 2 seats must be held (no partial holds from loser)");
    }

    @Test
    void concurrentConfirmPaidBooking_Idempotent_NoDuplicateTickets() throws Exception {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(userA);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(3));
        booking.setCreatedAt(LocalDateTime.now());

        Payment payment = new Payment();
        payment.setId("payment-1");
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100000.00"));

        SeatHold hold = new SeatHold();
        hold.setBooking(booking);
        hold.setSeat(seatA1);
        hold.setShowtime(sampleShowtime);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(hold));
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(List.of(payment));

        AtomicInteger ticketSaveCount = new AtomicInteger(0);
        when(ticketRepository.saveAllAndFlush(anyList())).thenAnswer(i -> {
            ticketSaveCount.incrementAndGet();
            return i.getArgument(0);
        });
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setBookingStatus(BookingStatus.PAID);
            return b;
        });

        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id("user-a")
                .email("usera@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        // First call confirms payment and creates ticket
        BookingDetailResponse resp1 = bookingService.confirmPaidBooking("booking-1", "payment-1");
        assertNotNull(resp1);
        assertEquals(BookingStatus.PAID, resp1.getBookingStatus());

        // Subsequent call is idempotent and does not re-create tickets
        BookingDetailResponse resp2 = bookingService.confirmPaidBooking("booking-1", "payment-1");
        assertNotNull(resp2);
        assertEquals(BookingStatus.PAID, resp2.getBookingStatus());

        assertEquals(1, ticketSaveCount.get(), "Tickets must only be saved once (idempotent confirmation)");
    }
}
