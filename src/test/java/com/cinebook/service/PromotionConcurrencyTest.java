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
import com.cinebook.service.impl.PromotionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromotionConcurrencyTest {

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

    private PromotionServiceImpl promotionService;
    private BookingServiceImpl bookingService;

    private User testUser;
    private Showtime testShowtime;
    private Promotion flashPromotion;

    @BeforeEach
    void setUp() {
        movieMapper = new MovieMapper(genreMapper);
        auditoriumMapper = new AuditoriumMapper(seatMapper);
        cinemaMapper = new CinemaMapper(auditoriumMapper);
        showtimeMapper = new ShowtimeMapper(movieMapper, cinemaMapper, auditoriumMapper);
        bookingMapper = new BookingMapper(showtimeMapper);

        promotionService = new PromotionServiceImpl(promotionRepository, promotionMapper);

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

        testUser = new User();
        testUser.setId("user-1");
        testUser.setEmail("customer@cinebook.com");

        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(testUser.getId())
                .email(testUser.getEmail())
                .fullName("Customer")
                .status(UserStatus.ACTIVE)
                .authorities(List.of(new SimpleGrantedAuthority("CUSTOMER")))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

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
        testShowtime.setStartTime(LocalDateTime.now().plusHours(2));
        testShowtime.setStatus(ShowtimeStatus.SCHEDULED);

        flashPromotion = new Promotion();
        flashPromotion.setId("promo-flash");
        flashPromotion.setCode("FLASH1");
        flashPromotion.setName("Flash Sale 1 Lượt");
        flashPromotion.setDiscountType(PromotionDiscountType.PERCENTAGE);
        flashPromotion.setDiscountValue(new BigDecimal("50.00"));
        flashPromotion.setStartAt(LocalDateTime.now().minusDays(1));
        flashPromotion.setEndAt(LocalDateTime.now().plusDays(1));
        flashPromotion.setUsageLimit(1); // Exactly 1 usage allowed
        flashPromotion.setUsedCount(0);
        flashPromotion.setStatus(PromotionStatus.ACTIVE);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(testShowtime));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(any(), any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Concurrency: 10 concurrent threads compete for 1 promotion usage -> Exactly 1 succeeds, 9 fail with 409 Conflict, usedCount=1")
    void testConcurrentPromotionApplication_ExactlyOneSucceeds() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ReentrantLock dbLock = new ReentrantLock();

        // Simulate DB pessimistic lock on findByCodeWithLock held until commit
        when(promotionRepository.findByCodeWithLock("FLASH1")).thenAnswer(i -> {
            dbLock.lock();
            return Optional.of(flashPromotion);
        });

        when(seatRepository.findByIdIn(any())).thenAnswer(i -> {
            Set<String> seatIds = i.getArgument(0);
            String seatId = seatIds.iterator().next();
            Seat s = new Seat();
            s.setId(seatId);
            s.setAuditorium(testShowtime.getAuditorium());
            s.setRowLabel("A");
            s.setSeatNumber((short) 1);
            s.setStatus(SeatStatus.ACTIVE);
            return List.of(s);
        });

        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(UUID.randomUUID().toString());
            return b;
        });

        when(seatHoldRepository.saveAllAndFlush(anyList())).thenAnswer(i -> {
            // Transaction commit point releases DB row lock
            if (dbLock.isHeldByCurrentThread()) {
                dbLock.unlock();
            }
            return i.getArgument(0);
        });

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int seatIndex = t + 1;
            executor.submit(() -> {
                try {
                    UserDetailsImpl userDetails = UserDetailsImpl.builder()
                            .id("user-1")
                            .email("customer@cinebook.com")
                            .fullName("Customer")
                            .status(UserStatus.ACTIVE)
                            .authorities(List.of(new SimpleGrantedAuthority("CUSTOMER")))
                            .build();
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
                    );

                    startLatch.await();
                    // Give each thread a unique seat to avoid seat hold conflict and isolate promotion quota conflict
                    CreateBookingRequest request = CreateBookingRequest.builder()
                            .showtimeId("showtime-1")
                            .seatIds(List.of("seat-" + seatIndex))
                            .promotionCode("FLASH1")
                            .build();

                    BookingDetailResponse res = bookingService.createBooking(request);
                    if (res != null) {
                        successCount.incrementAndGet();
                    }
                } catch (ConflictException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    // Other unexpected exceptions
                } finally {
                    if (dbLock.isHeldByCurrentThread()) {
                        dbLock.unlock();
                    }
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all 10 threads simultaneously
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(9);
        assertThat(flashPromotion.getUsedCount()).isEqualTo(1);
    }

}
