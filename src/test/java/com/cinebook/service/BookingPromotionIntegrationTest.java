package com.cinebook.service;

import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.BookingDetailResponse;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.mapper.*;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.BookingServiceImpl;
import com.cinebook.service.impl.PromotionServiceImpl;
import com.cinebook.task.BookingCleanupTask;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingPromotionIntegrationTest {

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
    private BookingCleanupTask bookingCleanupTask;

    private User testUser;
    private Showtime testShowtime;
    private Seat testSeat1;
    private Seat testSeat2;
    private Promotion percentagePromotion;
    private Promotion fixedPromotion;

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
                promotionMapper
        );

        bookingCleanupTask = new BookingCleanupTask(
                bookingRepository,
                seatHoldRepository,
                bookingPromotionRepository,
                promotionRepository
        );

        testUser = new User();
        testUser.setId("user-1");
        testUser.setEmail("customer@cinebook.com");

        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(testUser.getId())
                .email(testUser.getEmail())
                .fullName("Customer")
                .status(UserStatus.ACTIVE)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
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

        SeatType standardType = new SeatType();
        standardType.setId("st-std");
        standardType.setName("STANDARD");
        standardType.setPriceModifier(BigDecimal.ZERO);

        testSeat1 = new Seat();
        testSeat1.setId("seat-1");
        testSeat1.setAuditorium(auditorium);
        testSeat1.setRowLabel("A");
        testSeat1.setSeatNumber((short) 1);
        testSeat1.setSeatType(standardType);
        testSeat1.setStatus(SeatStatus.ACTIVE);

        testSeat2 = new Seat();
        testSeat2.setId("seat-2");
        testSeat2.setAuditorium(auditorium);
        testSeat2.setRowLabel("A");
        testSeat2.setSeatNumber((short) 2);
        testSeat2.setSeatType(standardType);
        testSeat2.setStatus(SeatStatus.ACTIVE);

        percentagePromotion = new Promotion();
        percentagePromotion.setId("promo-percent");
        percentagePromotion.setCode("SUMMER20");
        percentagePromotion.setName("Giảm 20% mùa hè");
        percentagePromotion.setDiscountType(PromotionDiscountType.PERCENTAGE);
        percentagePromotion.setDiscountValue(new BigDecimal("20.00"));
        percentagePromotion.setMinOrderAmount(new BigDecimal("150000.00"));
        percentagePromotion.setMaxDiscountAmount(new BigDecimal("50000.00"));
        percentagePromotion.setStartAt(LocalDateTime.now().minusDays(1));
        percentagePromotion.setEndAt(LocalDateTime.now().plusDays(5));
        percentagePromotion.setUsageLimit(100);
        percentagePromotion.setUsedCount(0);
        percentagePromotion.setStatus(PromotionStatus.ACTIVE);

        fixedPromotion = new Promotion();
        fixedPromotion.setId("promo-fix");
        fixedPromotion.setCode("FIX50K");
        fixedPromotion.setName("Giảm 50.000đ");
        fixedPromotion.setDiscountType(PromotionDiscountType.FIXED_AMOUNT);
        fixedPromotion.setDiscountValue(new BigDecimal("50000.00"));
        fixedPromotion.setStartAt(LocalDateTime.now().minusDays(1));
        fixedPromotion.setEndAt(LocalDateTime.now().plusDays(5));
        fixedPromotion.setUsageLimit(50);
        fixedPromotion.setUsedCount(0);
        fixedPromotion.setStatus(PromotionStatus.ACTIVE);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(testShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(testSeat1, testSeat2));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(any(), any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Create Booking with Percentage Promotion -> Discount computed, snapshot saved, total amount discounted")
    void testCreateBooking_WithPercentagePromotion_Success() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1", "seat-2"))
                .promotionCode("summer20")
                .build();

        when(promotionRepository.findByCodeWithLock("SUMMER20")).thenReturn(Optional.of(percentagePromotion));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId("booking-p1");
            return b;
        });

        BookingDetailResponse response = bookingService.createBooking(request);

        assertThat(response).isNotNull();
        // Gross: 100k + 100k = 200k. Discount 20% = 40k. Net Total = 160k.
        assertThat(response.getGrossAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("160000.00"));
        assertThat(response.getPromotion()).isNotNull();
        assertThat(response.getPromotion().getCode()).isEqualTo("SUMMER20");
        assertThat(response.getPromotion().getDiscountAmount()).isEqualByComparingTo(new BigDecimal("40000.00"));

        // Verify usedCount incremented
        assertThat(percentagePromotion.getUsedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Create Booking with Fixed Promotion -> Discount computed and subtracted")
    void testCreateBooking_WithFixedPromotion_Success() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1", "seat-2"))
                .promotionCode("FIX50K")
                .build();

        when(promotionRepository.findByCodeWithLock("FIX50K")).thenReturn(Optional.of(fixedPromotion));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId("booking-f1");
            return b;
        });

        BookingDetailResponse response = bookingService.createBooking(request);

        // Gross: 200k. Fixed discount: 50k. Net Total: 150k.
        assertThat(response.getGrossAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(fixedPromotion.getUsedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Create Booking with Expired Promotion -> Throws 400 Bad Request")
    void testCreateBooking_WithExpiredPromotion_ThrowsBadRequest() {
        percentagePromotion.setEndAt(LocalDateTime.now().minusMinutes(1));

        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1", "seat-2"))
                .promotionCode("SUMMER20")
                .build();

        when(promotionRepository.findByCodeWithLock("SUMMER20")).thenReturn(Optional.of(percentagePromotion));

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã hết hạn");

        assertThat(percentagePromotion.getUsedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Create Booking with Quota Exhausted Promotion -> Throws 409 Conflict")
    void testCreateBooking_WithQuotaExhausted_ThrowsConflict() {
        percentagePromotion.setUsedCount(100); // limit is 100

        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1", "seat-2"))
                .promotionCode("SUMMER20")
                .build();

        when(promotionRepository.findByCodeWithLock("SUMMER20")).thenReturn(Optional.of(percentagePromotion));

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã hết lượt sử dụng");
    }

    @Test
    @DisplayName("Cancel PENDING_PAYMENT Booking -> Releases reserved promotion quota (usedCount decrements by 1)")
    void testCancelBooking_ReleasesPromotionQuota() {
        percentagePromotion.setUsedCount(5);

        Booking booking = new Booking();
        booking.setId("booking-cancel-1");
        booking.setUser(testUser);
        booking.setShowtime(testShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("160000.00"));

        BookingPromotion bp = new BookingPromotion();
        bp.setId(new BookingPromotionId(percentagePromotion.getId(), booking.getId()));
        bp.setPromotion(percentagePromotion);
        bp.setBooking(booking);
        bp.setDiscountAmount(new BigDecimal("40000.00"));

        when(bookingRepository.findById("booking-cancel-1")).thenReturn(Optional.of(booking));
        when(bookingPromotionRepository.findByBookingId("booking-cancel-1")).thenReturn(List.of(bp));
        when(bookingPromotionRepository.findFirstByBookingId("booking-cancel-1")).thenReturn(Optional.of(bp));
        when(promotionRepository.findByIdWithLock(percentagePromotion.getId())).thenReturn(Optional.of(percentagePromotion));

        bookingService.cancelBooking("booking-cancel-1", new CancelBookingRequest("Customer changed mind"));

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        // Quota released from 5 to 4
        assertThat(percentagePromotion.getUsedCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("BookingCleanupTask: Expired PENDING_PAYMENT Booking -> Releases reserved promotion quota")
    void testHousekeepingCleanup_ReleasesPromotionQuota() {
        percentagePromotion.setUsedCount(3);

        Booking booking = new Booking();
        booking.setId("booking-expired-1");
        booking.setUser(testUser);
        booking.setShowtime(testShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));

        BookingPromotion bp = new BookingPromotion();
        bp.setId(new BookingPromotionId(percentagePromotion.getId(), booking.getId()));
        bp.setPromotion(percentagePromotion);
        bp.setBooking(booking);
        bp.setDiscountAmount(new BigDecimal("40000.00"));

        when(bookingRepository.findExpiredBookings(eq(BookingStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(booking));
        when(bookingPromotionRepository.findByBookingId("booking-expired-1")).thenReturn(List.of(bp));
        when(promotionRepository.findByIdWithLock(percentagePromotion.getId())).thenReturn(Optional.of(percentagePromotion));

        bookingCleanupTask.cleanupExpiredBookingsAndHolds();

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);
        // Quota released from 3 to 2
        assertThat(percentagePromotion.getUsedCount()).isEqualTo(2);
    }
}
