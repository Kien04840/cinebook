package com.cinebook.service;

import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.BookingDetailResponse;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.BadRequestException;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceSeatAdjacencyTest {

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

    private BookingServiceImpl bookingService;

    private User sampleUser;
    private Cinema sampleCinema;
    private Auditorium sampleAuditorium;
    private Movie sampleMovie;
    private Showtime sampleShowtime;
    private SeatType standardType;
    private SeatType coupleType;

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
                promotionRepository,
                bookingPromotionRepository,
                promotionService,
                bookingMapper,
                promotionMapper,
                emailService
        );

        sampleUser = new User();
        sampleUser.setId("user-1");
        sampleUser.setEmail("customer@cinebook.com");
        sampleUser.setStatus(UserStatus.ACTIVE);

        sampleCinema = new Cinema();
        sampleCinema.setId("cinema-1");
        sampleCinema.setStatus(CinemaStatus.ACTIVE);

        sampleAuditorium = new Auditorium();
        sampleAuditorium.setId("aud-1");
        sampleAuditorium.setName("Hall 1");
        sampleAuditorium.setCinema(sampleCinema);
        sampleAuditorium.setStatus(AuditoriumStatus.ACTIVE);

        sampleMovie = new Movie();
        sampleMovie.setId("movie-1");
        sampleMovie.setTitle("Inception");
        sampleMovie.setStatus(MovieStatus.NOW_SHOWING);

        sampleShowtime = new Showtime();
        sampleShowtime.setId("showtime-1");
        sampleShowtime.setMovie(sampleMovie);
        sampleShowtime.setAuditorium(sampleAuditorium);
        sampleShowtime.setStartTime(LocalDateTime.now().plusDays(1));
        sampleShowtime.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        sampleShowtime.setBasePrice(new BigDecimal("90000.00"));
        sampleShowtime.setStatus(ShowtimeStatus.SCHEDULED);

        standardType = new SeatType();
        standardType.setId("st-std");
        standardType.setName("Standard");
        standardType.setCode("STANDARD");
        standardType.setCapacity((short) 1);
        standardType.setPriceModifier(BigDecimal.ZERO);

        coupleType = new SeatType();
        coupleType.setId("st-cpl");
        coupleType.setName("Couple");
        coupleType.setCode("COUPLE");
        coupleType.setCapacity((short) 2);
        coupleType.setPriceModifier(new BigDecimal("30000.00"));

        setAuthenticatedUser(sampleUser.getId(), "CUSTOMER");

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(String userId, String role) {
        String authName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(userId)
                .email(userId + "@example.com")
                .authorities(List.of(new SimpleGrantedAuthority(authName)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    private Seat createSeat(String id, String row, short number, SeatType seatType) {
        Seat seat = new Seat();
        seat.setId(id);
        seat.setAuditorium(sampleAuditorium);
        seat.setRowLabel(row);
        seat.setSeatNumber(number);
        seat.setSeatType(seatType);
        seat.setStatus(SeatStatus.ACTIVE);
        return seat;
    }

    @Test
    @DisplayName("Adjacency: Selecting middle seat leaving 2 single orphan seats throws BadRequestException")
    void testCreateBooking_LeavingOrphanSeats_ThrowsException() {
        // Row A has A1, A2, A3 (all active, all available)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);

        when(seatRepository.findByIdIn(Set.of("s-a2"))).thenReturn(List.of(a2));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(Collections.emptyList());

        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a2"))
                .build();

        assertThatThrownBy(() -> bookingService.createBooking(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ghế trống đơn lẻ")
                .hasMessageContaining("hàng A");
    }

    @Test
    @DisplayName("Adjacency: Selecting contiguous pair leaving 2 seats does NOT violate adjacency")
    void testCreateBooking_ContiguousSelection_Success() {
        // Row A has A1, A2, A3, A4
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);

        when(seatRepository.findByIdIn(Set.of("s-a1", "s-a2"))).thenReturn(List.of(a1, a2));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(Collections.emptyList());

        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId("booking-new");
            b.setCreatedAt(LocalDateTime.now());
            return b;
        });

        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a1", "s-a2"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
        verify(seatHoldRepository, times(1)).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("Adjacency: Pre-existing orphan seat is not penalized if user does not create a new one")
    void testCreateBooking_PreExistingOrphan_Success() {
        // Row A: A1 (sold), A2 (available - isolated between sold A1 and sold A3!), A3 (sold), A4 (available), A5 (available)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);
        Seat a5 = createSeat("s-a5", "A", (short) 5, standardType);

        Ticket soldTicket1 = new Ticket();
        soldTicket1.setSeat(a1);
        Ticket soldTicket3 = new Ticket();
        soldTicket3.setSeat(a3);

        when(seatRepository.findByIdIn(Set.of("s-a4", "s-a5"))).thenReturn(List.of(a4, a5));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4, a5));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(List.of(soldTicket1, soldTicket3));

        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId("booking-new");
            b.setCreatedAt(LocalDateTime.now());
            return b;
        });

        // User books A4 and A5. Before: A2 was already orphan (count=1). After: A2 is still orphan (count=1). No new orphan!
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a4", "s-a5"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
    }

    @Test
    @DisplayName("Adjacency: Couple seat (capacity 2) remaining alone does NOT count as a single orphan seat")
    void testCreateBooking_CoupleSeatRemaining_Success() {
        // Row C: C1 (seatNumber 1, cap 2), C2 (seatNumber 3, cap 2)
        Seat c1 = createSeat("s-c1", "C", (short) 1, coupleType);
        Seat c2 = createSeat("s-c2", "C", (short) 3, coupleType);

        when(seatRepository.findByIdIn(Set.of("s-c1"))).thenReturn(List.of(c1));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "C"))
                .thenReturn(List.of(c1, c2));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(Collections.emptyList());

        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId("booking-new");
            b.setCreatedAt(LocalDateTime.now());
            return b;
        });

        // User books C1. C2 remains available (capacity 2). No single orphan seat!
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-c1"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
    }
}
