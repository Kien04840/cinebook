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

    private void mockBookingSave() {
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId("booking-new");
            b.setCreatedAt(LocalDateTime.now());
            return b;
        });
    }

    @Test
    @DisplayName("Case A: Single selection that does not increase orphan count is allowed")
    void testCaseA_SingleSelection_NoNewOrphan_Success() {
        // Row A: A1 (available), A2 (sold), A3 (sold), A4 (sold), A5 (sold)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);
        Seat a5 = createSeat("s-a5", "A", (short) 5, standardType);

        Ticket t2 = new Ticket(); t2.setSeat(a2);
        Ticket t3 = new Ticket(); t3.setSeat(a3);
        Ticket t4 = new Ticket(); t4.setSeat(a4);
        Ticket t5 = new Ticket(); t5.setSeat(a5);

        when(seatRepository.findByIdIn(Set.of("s-a1"))).thenReturn(List.of(a1));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4, a5));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(List.of(t2, t3, t4, t5));
        mockBookingSave();

        // Customer selects A1. Before: A1 orphan (1). After: 0 orphan. orphanAfter <= orphanBefore -> ALLOW
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a1"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
    }

    @Test
    @DisplayName("Case B: Single selection next to occupied seat leaving capacity >= 2 is allowed")
    void testCaseB_SingleSelection_NextToOccupied_Success() {
        // Row A: A1 (sold), A2 (available), A3 (available), A4 (available)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);

        Ticket t1 = new Ticket(); t1.setSeat(a1);

        when(seatRepository.findByIdIn(Set.of("s-a2"))).thenReturn(List.of(a2));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(List.of(t1));
        mockBookingSave();

        // Customer selects A2. Remaining: A3, A4 (capacity 2). orphanBefore = 0, orphanAfter = 0 -> ALLOW
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a2"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
    }

    @Test
    @DisplayName("Case C: Selection creates a new single orphan seat in the middle -> REJECT")
    void testCaseC_MiddleSelection_CreatesSingleOrphan_ThrowsException() {
        // Row A: A1 (sold), A2 (available), A3 (available), A4 (sold)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);

        Ticket t1 = new Ticket(); t1.setSeat(a1);
        Ticket t4 = new Ticket(); t4.setSeat(a4);

        when(seatRepository.findByIdIn(Set.of("s-a2"))).thenReturn(List.of(a2));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(List.of(t1, t4));

        // Customer selects A2, leaving A3 isolated between A2 and A4. orphanBefore = 0, orphanAfter = 1 -> REJECT
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
    @DisplayName("Case D: Selecting consecutive seats leaving >= 2 seats is allowed")
    void testCaseD_ConsecutiveSeats_NoNewOrphan_Success() {
        // Row A: A1, A2, A3, A4 (all available)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);

        when(seatRepository.findByIdIn(Set.of("s-a1", "s-a2"))).thenReturn(List.of(a1, a2));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        mockBookingSave();

        // Customer selects A1, A2. Remaining: A3, A4 (capacity 2). orphanBefore = 0, orphanAfter = 0 -> ALLOW
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a1", "s-a2"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
    }

    @Test
    @DisplayName("Case E: Booking multiple NON-adjacent seats that do not create orphans is allowed")
    void testCaseE_NonAdjacentSeats_NoNewOrphan_Success() {
        // Row A: A1, A2, A3, A4, A5, A6 (all available)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);
        Seat a5 = createSeat("s-a5", "A", (short) 5, standardType);
        Seat a6 = createSeat("s-a6", "A", (short) 6, standardType);

        when(seatRepository.findByIdIn(Set.of("s-a1", "s-a6"))).thenReturn(List.of(a1, a6));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4, a5, a6));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        mockBookingSave();

        // Customer selects A1 and A6 (NOT adjacent). Remaining: A2..A5 (continuous run of 4 seats). orphanBefore = 0, orphanAfter = 0 -> ALLOW
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a1", "s-a6"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
    }

    @Test
    @DisplayName("Case F: Selection leaves a single orphan at the end of the block -> REJECT")
    void testCaseF_EndSelection_LeavesOrphanAtRowEnd_ThrowsException() {
        // Row A: A1 (sold), A2 (available), A3 (available), A4 (available)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);

        Ticket t1 = new Ticket(); t1.setSeat(a1);

        when(seatRepository.findByIdIn(Set.of("s-a2", "s-a3"))).thenReturn(List.of(a2, a3));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(List.of(t1));

        // Customer selects A2, A3, leaving A4 alone at the end of row. orphanBefore = 0, orphanAfter = 1 -> REJECT
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a2", "s-a3"))
                .build();

        assertThatThrownBy(() -> bookingService.createBooking(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ghế trống đơn lẻ")
                .hasMessageContaining("hàng A");
    }

    @Test
    @DisplayName("Case G: Selection leaves a single orphan at the start of the block -> REJECT")
    void testCaseG_StartSelection_LeavesOrphanAtRowStart_ThrowsException() {
        // Row A: A1 (available), A2 (available), A3 (available), A4 (sold)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);

        Ticket t4 = new Ticket(); t4.setSeat(a4);

        when(seatRepository.findByIdIn(Set.of("s-a2", "s-a3"))).thenReturn(List.of(a2, a3));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(List.of(t4));

        // Customer selects A2, A3, leaving A1 alone at the start of row. orphanBefore = 0, orphanAfter = 1 -> REJECT
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a2", "s-a3"))
                .build();

        assertThatThrownBy(() -> bookingService.createBooking(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ghế trống đơn lẻ")
                .hasMessageContaining("hàng A");
    }

    @Test
    @DisplayName("Case H: Pre-existing orphan seat is tolerated when selection does not create new orphans -> ALLOW")
    void testCaseH_PreExistingOrphan_Tolerated_Success() {
        // Row A: A1 (sold), A2 (available - isolated), A3 (sold), A4 (available), A5 (available)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);
        Seat a5 = createSeat("s-a5", "A", (short) 5, standardType);

        Ticket t1 = new Ticket(); t1.setSeat(a1);
        Ticket t3 = new Ticket(); t3.setSeat(a3);

        when(seatRepository.findByIdIn(Set.of("s-a4", "s-a5"))).thenReturn(List.of(a4, a5));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4, a5));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(List.of(t1, t3));
        mockBookingSave();

        // Customer books A4, A5. orphanBefore = 1 (A2), orphanAfter = 1 (A2). No new orphan -> ALLOW
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a4", "s-a5"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
    }

    @Test
    @DisplayName("Case I: Empty Couple seat (capacity 2) remaining does NOT count as a single orphan seat -> ALLOW")
    void testCaseI_CoupleSeat_RemainingCapacityTwo_NotOrphan_Success() {
        // Row C: C1 (seatNumber 1, cap 2), C2 (seatNumber 3, cap 2)
        Seat c1 = createSeat("s-c1", "C", (short) 1, coupleType);
        Seat c2 = createSeat("s-c2", "C", (short) 3, coupleType);

        when(seatRepository.findByIdIn(Set.of("s-c1"))).thenReturn(List.of(c1));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "C"))
                .thenReturn(List.of(c1, c2));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        mockBookingSave();

        // Customer books C1. Remaining: C2 (capacity 2). orphanBefore = 0, orphanAfter = 0 -> ALLOW
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-c1"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
    }

    @Test
    @DisplayName("Case J: Aisle boundary partitions row into independent blocks")
    void testCaseJ_AisleBoundary_EvaluatedIndependently() {
        // Row A: A1 (seat 1), A2 (seat 2) | aisle (gap to 5) | A5 (seat 5), A6 (seat 6)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        Seat a5 = createSeat("s-a5", "A", (short) 5, standardType);
        Seat a6 = createSeat("s-a6", "A", (short) 6, standardType);

        when(seatRepository.findByIdIn(Set.of("s-a2"))).thenReturn(List.of(a2));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a5, a6));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(Collections.emptyList());

        // In Block 1 (A1, A2), booking A2 leaves A1 as an orphan! Even though A5 is in row A, aisle separates them.
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
    @DisplayName("Case K: Broken/non-active seat creates block boundary and is not counted as sellable seat")
    void testCaseK_BrokenSeatBoundary_NotCountedAsSellable() {
        // Row A: A1 (ACTIVE), A2 (BROKEN - non-active), A3 (ACTIVE), A4 (ACTIVE)
        Seat a1 = createSeat("s-a1", "A", (short) 1, standardType);
        Seat a2 = createSeat("s-a2", "A", (short) 2, standardType);
        a2.setStatus(SeatStatus.BROKEN);
        Seat a3 = createSeat("s-a3", "A", (short) 3, standardType);
        Seat a4 = createSeat("s-a4", "A", (short) 4, standardType);

        when(seatRepository.findByIdIn(Set.of("s-a3", "s-a4"))).thenReturn(List.of(a3, a4));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "A"))
                .thenReturn(List.of(a1, a2, a3, a4));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        mockBookingSave();

        // Customer books A3, A4. Block 1 (A1) has pre-existing orphan A1. Block 2 has A3, A4 which are fully booked.
        // orphanBefore = 1 (A1), orphanAfter = 1 (A1). No new orphan -> ALLOW
        CreateBookingRequest req = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("s-a3", "s-a4"))
                .build();

        BookingDetailResponse res = bookingService.createBooking(req);
        assertThat(res).isNotNull();
    }
}
