package com.cinebook.service;

import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.BookingDetailResponse;
import com.cinebook.dto.response.TicketPricingBreakdown;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.mapper.*;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServicePricingSnapshotTest {

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

    @Mock
    private PricingService pricingService;

    private BookingMapper bookingMapper;
    private BookingServiceImpl bookingService;

    private User sampleUser;
    private Cinema sampleCinema;
    private Auditorium sampleAuditorium;
    private Movie sampleMovie;
    private Showtime sampleShowtime;
    private SeatType standardType;
    private Seat seat1;

    @BeforeEach
    void setUp() {
        GenreMapper genreMapper = new GenreMapper();
        MovieMapper movieMapper = new MovieMapper(genreMapper);
        SeatMapper seatMapper = new SeatMapper();
        AuditoriumMapper auditoriumMapper = new AuditoriumMapper(seatMapper);
        CinemaMapper cinemaMapper = new CinemaMapper(auditoriumMapper);
        ShowtimeMapper showtimeMapper = new ShowtimeMapper(movieMapper, cinemaMapper, auditoriumMapper);
        bookingMapper = new BookingMapper(showtimeMapper);
        PromotionMapper promotionMapper = new PromotionMapper();

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
                emailService,
                pricingService
        );

        sampleUser = new User();
        sampleUser.setId("user-1");
        sampleUser.setEmail("user@cinebook.com");
        sampleUser.setFullName("Nguyen Van A");
        sampleUser.setStatus(UserStatus.ACTIVE);

        sampleCinema = new Cinema();
        sampleCinema.setId("cinema-1");
        sampleCinema.setName("CineBook Central");
        sampleCinema.setStatus(CinemaStatus.ACTIVE);

        sampleAuditorium = new Auditorium();
        sampleAuditorium.setId("aud-1");
        sampleAuditorium.setName("Screen 1");
        sampleAuditorium.setCinema(sampleCinema);
        sampleAuditorium.setStatus(AuditoriumStatus.ACTIVE);

        sampleMovie = new Movie();
        sampleMovie.setId("movie-1");
        sampleMovie.setTitle("Test Movie");
        sampleMovie.setStatus(MovieStatus.NOW_SHOWING);

        sampleShowtime = new Showtime();
        sampleShowtime.setId("st-1");
        sampleShowtime.setMovie(sampleMovie);
        sampleShowtime.setAuditorium(sampleAuditorium);
        sampleShowtime.setStartTime(LocalDateTime.now().plusDays(1));
        sampleShowtime.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        sampleShowtime.setBasePrice(new BigDecimal("75000"));
        sampleShowtime.setStatus(ShowtimeStatus.SCHEDULED);

        standardType = new SeatType();
        standardType.setId("type-standard");
        standardType.setCode("STANDARD");
        standardType.setName("Thường");
        standardType.setPriceModifier(BigDecimal.ZERO);

        seat1 = new Seat();
        seat1.setId("seat-1");
        seat1.setAuditorium(sampleAuditorium);
        seat1.setRowLabel("A");
        seat1.setSeatNumber((short) 5);
        seat1.setSeatType(standardType);
        seat1.setStatus(SeatStatus.ACTIVE);

        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id("user-1")
                .email("user@cinebook.com")
                .fullName("Nguyen Van A")
                .authorities(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .status(UserStatus.ACTIVE)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Ticket Price Snapshot: Ticket created with Rule +10k preserves price even when rule later increases to +20k")
    void testTicketPriceSnapshot_PreservesHistoricalPrice() {
        // Step 1: PricingService calculates initial price with +10k day rule (e.g. 75k base + 10k = 85k)
        TicketPricingBreakdown rule10k = TicketPricingBreakdown.builder()
                .basePrice(new BigDecimal("75000"))
                .seatTypeModifier(BigDecimal.ZERO)
                .dayModifier(new BigDecimal("10000"))
                .timeSlotModifier(BigDecimal.ZERO)
                .finalPrice(new BigDecimal("85000"))
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById("st-1")).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));
        when(pricingService.calculateShowtimeBaseBreakdown(sampleShowtime)).thenReturn(rule10k);
        when(pricingService.calculateTicketPrice(eq(rule10k), any())).thenReturn(rule10k);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) b.setId("booking-1");
            return b;
        });
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) b.setId("booking-1");
            return b;
        });

        // Create booking under 10k rule -> totalAmount = 85,000
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("st-1")
                .seatIds(List.of("seat-1"))
                .build();

        BookingDetailResponse bookingRes = bookingService.createBooking(req);
        assertThat(bookingRes.getTotalAmount()).isEqualByComparingTo("85000");

        // Simulate payment success and ticket creation under rule 10k
        SeatHold hold1 = new SeatHold();
        hold1.setId(1L);
        hold1.setSeat(seat1);
        hold1.setShowtime(sampleShowtime);

        Booking existingBooking = new Booking();
        existingBooking.setId("booking-1");
        existingBooking.setBookingCode("CB-20260907-001");
        existingBooking.setShowtime(sampleShowtime);
        existingBooking.setUser(sampleUser);
        existingBooking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        existingBooking.setTotalAmount(new BigDecimal("85000"));
        existingBooking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));

        Payment payment = new Payment();
        payment.setId("pay-1");
        payment.setBooking(existingBooking);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("85000"));

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(existingBooking));
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(hold1));

        bookingService.confirmPaidBooking("booking-1", "pay-1");

        // Verify ticket saved with ticketPrice = 85,000 (Snapshotted!)
        verify(ticketRepository).saveAllAndFlush(argThat(tickets -> {
            List<Ticket> list = (List<Ticket>) tickets;
            return list.size() == 1 && list.get(0).getTicketPrice().compareTo(new BigDecimal("85000")) == 0;
        }));

        // Step 2: Now admin changes rule to +20k (75k + 20k = 95k)
        TicketPricingBreakdown rule20k = TicketPricingBreakdown.builder()
                .basePrice(new BigDecimal("75000"))
                .seatTypeModifier(BigDecimal.ZERO)
                .dayModifier(new BigDecimal("20000"))
                .timeSlotModifier(BigDecimal.ZERO)
                .finalPrice(new BigDecimal("95000"))
                .build();

        when(pricingService.calculateShowtimeBaseBreakdown(sampleShowtime)).thenReturn(rule20k);
        when(pricingService.calculateTicketPrice(eq(rule20k), any())).thenReturn(rule20k);

        // A new booking created after rule change gets the new price (95k)
        BookingDetailResponse newBookingRes = bookingService.createBooking(req);
        assertThat(newBookingRes.getTotalAmount()).isEqualByComparingTo("95000");
    }
}
