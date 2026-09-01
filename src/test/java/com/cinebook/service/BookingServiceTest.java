package com.cinebook.service;

import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.*;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.*;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

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
    private User adminUser;
    private Cinema sampleCinema;
    private Auditorium sampleAuditorium;
    private Movie sampleMovie;
    private Showtime sampleShowtime;
    private SeatType standardType;
    private SeatType vipType;
    private Seat seat1;
    private Seat seat2;

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
        sampleUser.setFullName("Nguyen Van A");
        sampleUser.setStatus(UserStatus.ACTIVE);

        adminUser = new User();
        adminUser.setId("admin-1");
        adminUser.setEmail("admin@cinebook.com");
        adminUser.setFullName("System Admin");
        adminUser.setStatus(UserStatus.ACTIVE);

        sampleCinema = new Cinema();
        sampleCinema.setId("cinema-1");
        sampleCinema.setName("CineBook Landmark 81");
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
        sampleMovie.setDurationMinutes((short) 148);

        sampleShowtime = new Showtime();
        sampleShowtime.setId("showtime-1");
        sampleShowtime.setMovie(sampleMovie);
        sampleShowtime.setAuditorium(sampleAuditorium);
        sampleShowtime.setStartTime(LocalDateTime.now().plusDays(1).withHour(19).withMinute(0));
        sampleShowtime.setEndTime(LocalDateTime.now().plusDays(1).withHour(21).withMinute(28));
        sampleShowtime.setBasePrice(new BigDecimal("100000.00"));
        sampleShowtime.setStatus(ShowtimeStatus.SCHEDULED);

        standardType = new SeatType();
        standardType.setId("st-std");
        standardType.setName("STANDARD");
        standardType.setPriceModifier(BigDecimal.ZERO);

        vipType = new SeatType();
        vipType.setId("st-vip");
        vipType.setName("VIP");
        vipType.setPriceModifier(new BigDecimal("20000.00"));

        seat1 = new Seat();
        seat1.setId("seat-1");
        seat1.setAuditorium(sampleAuditorium);
        seat1.setRowLabel("A");
        seat1.setSeatNumber((short) 1);
        seat1.setSeatType(standardType);
        seat1.setStatus(SeatStatus.ACTIVE);

        seat2 = new Seat();
        seat2.setId("seat-2");
        seat2.setAuditorium(sampleAuditorium);
        seat2.setRowLabel("A");
        seat2.setSeatNumber((short) 2);
        seat2.setSeatType(vipType);
        seat2.setStatus(SeatStatus.ACTIVE);

        setAuthenticatedUser(sampleUser.getId(), "CUSTOMER");
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

    // ==========================================
    // 1. Create Booking Tests
    // ==========================================

    @Test
    void createBooking_Success() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of(seat1.getId(), seat2.getId()))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1, seat2));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq(sampleShowtime.getId()), any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq(sampleShowtime.getId()), any(), any())).thenReturn(Collections.emptyList());
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId("booking-1");
            b.setCreatedAt(LocalDateTime.now());
            return b;
        });

        BookingDetailResponse response = bookingService.createBooking(request);

        assertNotNull(response);
        assertEquals(BookingStatus.PENDING_PAYMENT, response.getBookingStatus());
        assertEquals(new BigDecimal("220000.00"), response.getTotalAmount()); // 100k + (100k+20k)
        assertNotNull(response.getHoldExpiresAt());
        assertEquals(2, response.getSeats().size());
        verify(seatHoldRepository, times(1)).deleteExpiredHoldsForSeats(eq(sampleShowtime.getId()), any(), any());
        verify(bookingRepository, times(1)).saveAndFlush(any(Booking.class));
        verify(seatHoldRepository, times(1)).saveAllAndFlush(anyList());
    }

    @Test
    void createBooking_ExceedMaxSeats_ThrowsBadRequest() {
        List<String> nineSeats = List.of("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9");
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(nineSeats)
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("Không thể đặt quá 8 ghế"));
    }

    @Test
    void createBooking_EmptySeats_ThrowsBadRequest() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(Collections.emptyList())
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("Danh sách ghế không được để trống"));
    }

    @Test
    void createBooking_DuplicateSeats_ThrowsBadRequest() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of("seat-1", "seat-1"))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("trùng lặp"));
    }

    @Test
    void createBooking_ShowtimeNotFound_ThrowsResourceNotFound() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("invalid-st")
                .seatIds(List.of("seat-1"))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById("invalid-st")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void createBooking_ShowtimeCancelled_ThrowsBadRequest() {
        sampleShowtime.setStatus(ShowtimeStatus.CANCELLED);
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of("seat-1"))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("Lịch chiếu đã bị hủy"));
    }

    @Test
    void createBooking_ShowtimeInPast_ThrowsBadRequest() {
        sampleShowtime.setStartTime(LocalDateTime.now().minusHours(1));
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of("seat-1"))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("Lịch chiếu đã bắt đầu hoặc đã qua"));
    }

    @Test
    void createBooking_AuditoriumNotActive_ThrowsConflict() {
        sampleAuditorium.setStatus(AuditoriumStatus.MAINTENANCE);
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of("seat-1"))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));

        ConflictException ex = assertThrows(ConflictException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("bảo trì"));
    }

    @Test
    void createBooking_CinemaNotActive_ThrowsConflict() {
        sampleCinema.setStatus(CinemaStatus.CLOSED);
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of("seat-1"))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));

        ConflictException ex = assertThrows(ConflictException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("Rạp chiếu phim hiện không hoạt động"));
    }

    @Test
    void createBooking_SeatNotFound_ThrowsResourceNotFound() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of("seat-1", "non-existent"))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));

        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void createBooking_SeatWrongAuditorium_ThrowsBadRequest() {
        Auditorium otherAud = new Auditorium();
        otherAud.setId("other-aud");
        seat2.setAuditorium(otherAud);

        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of(seat1.getId(), seat2.getId()))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1, seat2));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("không thuộc phòng chiếu"));
    }

    @Test
    void createBooking_SeatBroken_ThrowsBadRequest() {
        seat2.setStatus(SeatStatus.BROKEN);

        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of(seat1.getId(), seat2.getId()))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1, seat2));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("BROKEN"));
    }

    @Test
    void createBooking_SeatAlreadyHeld_ThrowsConflict() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of(seat1.getId()))
                .build();

        SeatHold activeHold = new SeatHold();
        activeHold.setSeat(seat1);

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq(sampleShowtime.getId()), any(), any())).thenReturn(List.of(activeHold));

        ConflictException ex = assertThrows(ConflictException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("giữ chỗ"));
    }

    @Test
    void createBooking_SeatAlreadySold_ThrowsConflict() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of(seat1.getId()))
                .build();

        Ticket soldTicket = new Ticket();
        soldTicket.setSeat(seat1);
        soldTicket.setTicketStatus(TicketStatus.VALID);

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq(sampleShowtime.getId()), any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq(sampleShowtime.getId()), any(), any())).thenReturn(List.of(soldTicket));

        ConflictException ex = assertThrows(ConflictException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("đã được bán"));
    }

    @Test
    void createBooking_UsedTicket_ThrowsConflict() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of(seat1.getId()))
                .build();

        Ticket usedTicket = new Ticket();
        usedTicket.setSeat(seat1);
        usedTicket.setTicketStatus(TicketStatus.USED);

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq(sampleShowtime.getId()), any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq(sampleShowtime.getId()), any(), any())).thenReturn(List.of(usedTicket));

        ConflictException ex = assertThrows(ConflictException.class, () -> bookingService.createBooking(request));
        assertTrue(ex.getMessage().contains("đã được bán"));
    }

    @Test
    void createBooking_ExpiredHold_ResolvedImmediately_Succeeds() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of(seat1.getId()))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));
        when(seatHoldRepository.deleteExpiredHoldsForSeats(eq(sampleShowtime.getId()), any(), any())).thenReturn(1);
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq(sampleShowtime.getId()), any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq(sampleShowtime.getId()), any(), any())).thenReturn(Collections.emptyList());
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId("booking-1");
            b.setCreatedAt(LocalDateTime.now());
            return b;
        });

        BookingDetailResponse response = bookingService.createBooking(request);

        assertNotNull(response);
        assertEquals(BookingStatus.PENDING_PAYMENT, response.getBookingStatus());
        verify(seatHoldRepository, times(1)).deleteExpiredHoldsForSeats(eq(sampleShowtime.getId()), any(), any());
    }

    @Test
    void createBooking_ConcurrencyConflict_ThrowsConflict() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId(sampleShowtime.getId())
                .seatIds(List.of(seat1.getId()))
                .build();

        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq(sampleShowtime.getId()), any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq(sampleShowtime.getId()), any(), any())).thenReturn(Collections.emptyList());
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(seatHoldRepository.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("Duplicate entry uk_seat_holds_showtime_seat"));

        assertThrows(ConflictException.class, () -> bookingService.createBooking(request));
    }

    // ==========================================
    // 2. Get Booking Detail Tests
    // ==========================================

    @Test
    void getBookingDetail_Owner_Success() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-20260901-ABC123");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setCreatedAt(LocalDateTime.now());

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.getBookingDetail("booking-1");

        assertNotNull(response);
        assertEquals("booking-1", response.getId());
        assertEquals("CB-20260901-ABC123", response.getBookingCode());
    }

    @Test
    void getBookingDetail_Admin_Success() {
        setAuthenticatedUser(adminUser.getId(), "ADMIN");

        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PAID);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setCreatedAt(LocalDateTime.now());

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.getBookingDetail("booking-1");

        assertNotNull(response);
        assertEquals("booking-1", response.getId());
    }

    @Test
    void getBookingDetail_NotOwner_ThrowsForbidden() {
        User otherUser = new User();
        otherUser.setId("other-user");

        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(otherUser);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        assertThrows(ForbiddenException.class, () -> bookingService.getBookingDetail("booking-1"));
    }

    // ==========================================
    // 3. Get My Bookings Tests
    // ==========================================

    @Test
    void getMyBookings_Success() {
        Booking b1 = new Booking();
        b1.setId("b1");
        b1.setUser(sampleUser);
        b1.setShowtime(sampleShowtime);
        b1.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        b1.setTotalAmount(new BigDecimal("100000.00"));
        b1.setCreatedAt(LocalDateTime.now());

        Pageable pageable = PageRequest.of(0, 20);
        Page<Booking> page = new PageImpl<>(List.of(b1), pageable, 1);

        when(bookingRepository.findByUserId(sampleUser.getId(), pageable)).thenReturn(page);

        PageResponse<BookingSummaryResponse> response = bookingService.getMyBookings(null, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("b1", response.getContent().get(0).getId());
    }

    // ==========================================
    // 4. Cancel Booking Tests
    // ==========================================

    @Test
    void cancelBooking_PendingPayment_Success() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setCreatedAt(LocalDateTime.now());

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());

        CancelBookingRequest cancelReq = CancelBookingRequest.builder().reason("Change of plans").build();
        BookingDetailResponse response = bookingService.cancelBooking("booking-1", cancelReq);

        assertNotNull(response);
        assertEquals(BookingStatus.CANCELLED, response.getBookingStatus());
        verify(seatHoldRepository, times(1)).deleteByBookingId("booking-1");
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    void cancelBooking_Paid_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(sampleUser);
        booking.setBookingStatus(BookingStatus.PAID);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bookingService.cancelBooking("booking-1", null));
        assertTrue(ex.getMessage().contains("Không thể tự hủy đơn đặt vé đã thanh toán thành công"));
    }

    @Test
    void cancelBooking_AlreadyCancelled_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(sampleUser);
        booking.setBookingStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        assertThrows(BadRequestException.class, () -> bookingService.cancelBooking("booking-1", null));
    }

    @Test
    void cancelBooking_Expired_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(sampleUser);
        booking.setBookingStatus(BookingStatus.EXPIRED);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        assertThrows(BadRequestException.class, () -> bookingService.cancelBooking("booking-1", null));
    }

    // ==========================================
    // 5. Confirm Paid Booking Tests
    // ==========================================

    @Test
    void confirmPaidBooking_Success() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(sampleUser);
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
        hold.setSeat(seat1);
        hold.setShowtime(sampleShowtime);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(List.of(hold));
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(List.of(payment));

        BookingDetailResponse response = bookingService.confirmPaidBooking("booking-1", "payment-1");

        assertNotNull(response);
        assertEquals(BookingStatus.PAID, response.getBookingStatus());
        verify(ticketRepository, times(1)).saveAllAndFlush(anyList());
        verify(seatHoldRepository, times(1)).deleteByBookingId("booking-1");
    }

    @Test
    void confirmPaidBooking_Idempotent_WhenAlreadyPaid() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PAID);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setCreatedAt(LocalDateTime.now());

        Payment payment = new Payment();
        payment.setId("payment-1");
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100000.00"));

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBookingId("booking-1")).thenReturn(List.of(payment));

        BookingDetailResponse response = bookingService.confirmPaidBooking("booking-1", "payment-1");

        assertNotNull(response);
        assertEquals(BookingStatus.PAID, response.getBookingStatus());
        verify(ticketRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void confirmPaidBooking_HoldsDeletedAndNoTickets_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(3));

        Payment payment = new Payment();
        payment.setId("payment-1");
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100000.00"));

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        when(seatHoldRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());
        when(ticketRepository.findByBookingId("booking-1")).thenReturn(Collections.emptyList());

        assertThrows(BadRequestException.class, () -> bookingService.confirmPaidBooking("booking-1", "payment-1"));
    }

    @Test
    void confirmPaidBooking_PaymentNotBelongToBooking_ThrowsBadRequest() {
        Booking booking1 = new Booking();
        booking1.setId("booking-1");

        Booking booking2 = new Booking();
        booking2.setId("booking-2");

        Payment payment = new Payment();
        payment.setId("payment-1");
        payment.setBooking(booking2);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking1));
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));

        assertThrows(BadRequestException.class, () -> bookingService.confirmPaidBooking("booking-1", "payment-1"));
    }

    @Test
    void confirmPaidBooking_PaymentStatusNotSuccess_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("booking-1");

        Payment payment = new Payment();
        payment.setId("payment-1");
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.FAILED);

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));

        assertThrows(BadRequestException.class, () -> bookingService.confirmPaidBooking("booking-1", "payment-1"));
    }

    @Test
    void confirmPaidBooking_AmountMismatch_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setTotalAmount(new BigDecimal("100000.00"));

        Payment payment = new Payment();
        payment.setId("payment-1");
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("50000.00"));

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));

        assertThrows(BadRequestException.class, () -> bookingService.confirmPaidBooking("booking-1", "payment-1"));
    }

    @Test
    void confirmPaidBooking_BookingExpired_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("100000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired!

        Payment payment = new Payment();
        payment.setId("payment-1");
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100000.00"));

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));

        assertThrows(BadRequestException.class, () -> bookingService.confirmPaidBooking("booking-1", "payment-1"));
    }

    // ==========================================
    // 6. Showtime Seat Availability Tests
    // ==========================================

    @Test
    void getShowtimeSeatAvailability_Success() {
        Seat seatBroken = new Seat();
        seatBroken.setId("seat-3");
        seatBroken.setAuditorium(sampleAuditorium);
        seatBroken.setRowLabel("B");
        seatBroken.setSeatNumber((short) 1);
        seatBroken.setStatus(SeatStatus.BROKEN);

        SeatHold activeHold = new SeatHold();
        activeHold.setSeat(seat1);

        Ticket soldTicket = new Ticket();
        soldTicket.setSeat(seat2);
        soldTicket.setTicketStatus(TicketStatus.VALID);

        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc("aud-1")).thenReturn(List.of(seat1, seat2, seatBroken));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(List.of(activeHold));
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(List.of(soldTicket));

        List<ShowtimeSeatStatusResponse> availability = bookingService.getShowtimeSeatAvailability("showtime-1");

        assertNotNull(availability);
        assertEquals(3, availability.size());
        assertEquals(SeatAvailabilityStatus.HELD, availability.get(0).getAvailabilityStatus());
        assertEquals(SeatAvailabilityStatus.SOLD, availability.get(1).getAvailabilityStatus());
        assertEquals(SeatAvailabilityStatus.BLOCKED, availability.get(2).getAvailabilityStatus());
    }

    @Test
    void getShowtimeSeatAvailability_UsedTicket_ReturnsSold() {
        Ticket usedTicket = new Ticket();
        usedTicket.setSeat(seat1);
        usedTicket.setTicketStatus(TicketStatus.USED);

        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc("aud-1")).thenReturn(List.of(seat1));
        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq("showtime-1"), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq("showtime-1"), any())).thenReturn(List.of(usedTicket));

        List<ShowtimeSeatStatusResponse> availability = bookingService.getShowtimeSeatAvailability("showtime-1");

        assertNotNull(availability);
        assertEquals(1, availability.size());
        assertEquals(SeatAvailabilityStatus.SOLD, availability.get(0).getAvailabilityStatus());
    }

    // ==========================================
    // REFUND TESTS
    // ==========================================

    @Test
    void processBookingRefund_PaidBooking_Success() {
        Booking paidBooking = new Booking();
        paidBooking.setId("booking-paid-1");
        paidBooking.setBookingStatus(BookingStatus.PAID);
        paidBooking.setTotalAmount(new BigDecimal("180000.00"));
        paidBooking.setUser(sampleUser);
        paidBooking.setShowtime(sampleShowtime);

        Ticket validTicket = new Ticket();
        validTicket.setId("ticket-1");
        validTicket.setBooking(paidBooking);
        validTicket.setSeat(seat1);
        validTicket.setTicketStatus(TicketStatus.VALID);

        when(bookingRepository.findByIdWithLock("booking-paid-1")).thenReturn(Optional.of(paidBooking));
        when(ticketRepository.findByBookingId("booking-paid-1")).thenReturn(List.of(validTicket));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingDetailResponse result = bookingService.processBookingRefund("booking-paid-1", "Khách hủy vé", sampleUser.getId());

        assertNotNull(result);
        assertEquals(BookingStatus.REFUNDED, result.getBookingStatus());
        assertEquals(BookingStatus.REFUNDED, paidBooking.getBookingStatus());
        assertEquals(TicketStatus.CANCELLED, validTicket.getTicketStatus());
        verify(ticketRepository).saveAll(List.of(validTicket));
        verify(promotionRepository, never()).save(any());
    }

    @Test
    void processBookingRefund_UsedTicket_ThrowsBadRequest() {
        Booking paidBooking = new Booking();
        paidBooking.setId("booking-paid-1");
        paidBooking.setBookingStatus(BookingStatus.PAID);

        Ticket usedTicket = new Ticket();
        usedTicket.setId("ticket-used");
        usedTicket.setTicketStatus(TicketStatus.USED);

        when(bookingRepository.findByIdWithLock("booking-paid-1")).thenReturn(Optional.of(paidBooking));
        when(ticketRepository.findByBookingId("booking-paid-1")).thenReturn(List.of(usedTicket));

        assertThrows(BadRequestException.class, () ->
                bookingService.processBookingRefund("booking-paid-1", "Hủy", sampleUser.getId()));
    }

    @Test
    void processBookingRefund_InvalidStatus_ThrowsBadRequest() {
        Booking pendingBooking = new Booking();
        pendingBooking.setId("booking-pending-1");
        pendingBooking.setBookingStatus(BookingStatus.PENDING_PAYMENT);

        when(bookingRepository.findByIdWithLock("booking-pending-1")).thenReturn(Optional.of(pendingBooking));

        assertThrows(BadRequestException.class, () ->
                bookingService.processBookingRefund("booking-pending-1", "Hủy", sampleUser.getId()));
    }

    @Test
    void processBookingRefund_AlreadyRefunded_Idempotent() {
        Booking refundedBooking = new Booking();
        refundedBooking.setId("booking-ref-1");
        refundedBooking.setBookingStatus(BookingStatus.REFUNDED);
        refundedBooking.setUser(sampleUser);
        refundedBooking.setShowtime(sampleShowtime);

        when(bookingRepository.findByIdWithLock("booking-ref-1")).thenReturn(Optional.of(refundedBooking));
        when(bookingRepository.findById("booking-ref-1")).thenReturn(Optional.of(refundedBooking));

        BookingDetailResponse result = bookingService.processBookingRefund("booking-ref-1", "Hủy lại", sampleUser.getId());

        assertNotNull(result);
        assertEquals(BookingStatus.REFUNDED, result.getBookingStatus());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void getShowtimeSeatAvailability_WhenUserHasActiveHold_MarksIsHeldByCurrentUserTrue() {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(sampleUser.getId())
                .email(sampleUser.getEmail())
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(sampleAuditorium.getId()))
                .thenReturn(List.of(seat1, seat2));

        Booking userBooking = new Booking();
        userBooking.setId("b-1");
        userBooking.setUser(sampleUser);

        SeatHold userHold = new SeatHold();
        userHold.setId(10L);
        userHold.setSeat(seat1);
        userHold.setBooking(userBooking);
        userHold.setExpiresAt(LocalDateTime.now().plusMinutes(3));

        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq(sampleShowtime.getId()), any(LocalDateTime.class)))
                .thenReturn(List.of(userHold));
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq(sampleShowtime.getId()), any()))
                .thenReturn(Collections.emptyList());

        List<ShowtimeSeatStatusResponse> seatStatuses = bookingService.getShowtimeSeatAvailability(sampleShowtime.getId());

        assertEquals(2, seatStatuses.size());
        ShowtimeSeatStatusResponse s1 = seatStatuses.stream().filter(s -> s.getId().equals(seat1.getId())).findFirst().orElseThrow();
        ShowtimeSeatStatusResponse s2 = seatStatuses.stream().filter(s -> s.getId().equals(seat2.getId())).findFirst().orElseThrow();

        assertEquals(SeatAvailabilityStatus.HELD, s1.getAvailabilityStatus());
        assertTrue(s1.getIsHeldByCurrentUser());

        assertEquals(SeatAvailabilityStatus.AVAILABLE, s2.getAvailabilityStatus());
        assertFalse(s2.getIsHeldByCurrentUser());
    }

    @Test
    void getShowtimeSeatAvailability_WhenOtherUserHasHold_MarksIsHeldByCurrentUserFalse() {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(sampleUser.getId())
                .email(sampleUser.getEmail())
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        when(showtimeRepository.findById(sampleShowtime.getId())).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(sampleAuditorium.getId()))
                .thenReturn(List.of(seat1));

        User otherUser = new User();
        otherUser.setId("other-user-id");

        Booking otherBooking = new Booking();
        otherBooking.setId("b-other");
        otherBooking.setUser(otherUser);

        SeatHold otherHold = new SeatHold();
        otherHold.setId(11L);
        otherHold.setSeat(seat1);
        otherHold.setBooking(otherBooking);
        otherHold.setExpiresAt(LocalDateTime.now().plusMinutes(3));

        when(seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(eq(sampleShowtime.getId()), any(LocalDateTime.class)))
                .thenReturn(List.of(otherHold));
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq(sampleShowtime.getId()), any()))
                .thenReturn(Collections.emptyList());

        List<ShowtimeSeatStatusResponse> seatStatuses = bookingService.getShowtimeSeatAvailability(sampleShowtime.getId());

        assertEquals(1, seatStatuses.size());
        assertEquals(SeatAvailabilityStatus.HELD, seatStatuses.get(0).getAvailabilityStatus());
        assertFalse(seatStatuses.get(0).getIsHeldByCurrentUser());
    }

    @Test
    void getActiveBookingForShowtime_WhenActiveBookingExists_ReturnsDetail() {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(sampleUser.getId())
                .email(sampleUser.getEmail())
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        Booking activeB = new Booking();
        activeB.setId("active-b-1");
        activeB.setBookingCode("CB-ACTIVE-001");
        activeB.setUser(sampleUser);
        activeB.setShowtime(sampleShowtime);
        activeB.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        activeB.setHoldExpiresAt(LocalDateTime.now().plusMinutes(4));

        when(bookingRepository.findActiveBookingsByUserAndShowtime(eq(sampleUser.getId()), eq(sampleShowtime.getId()), any(LocalDateTime.class)))
                .thenReturn(List.of(activeB));
        when(seatHoldRepository.findByBookingId("active-b-1")).thenReturn(List.of());
        when(paymentRepository.findByBookingId("active-b-1")).thenReturn(List.of());
        when(bookingPromotionRepository.findFirstByBookingId("active-b-1")).thenReturn(Optional.empty());

        BookingDetailResponse response = bookingService.getActiveBookingForShowtime(sampleShowtime.getId());

        assertNotNull(response);
        assertEquals("active-b-1", response.getId());
        assertEquals("CB-ACTIVE-001", response.getBookingCode());
    }

    @Test
    void getActiveBookingForShowtime_WhenNoActiveBooking_ReturnsNull() {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(sampleUser.getId())
                .email(sampleUser.getEmail())
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        when(bookingRepository.findActiveBookingsByUserAndShowtime(eq(sampleUser.getId()), eq(sampleShowtime.getId()), any(LocalDateTime.class)))
                .thenReturn(List.of());

        BookingDetailResponse response = bookingService.getActiveBookingForShowtime(sampleShowtime.getId());

        assertNull(response);
    }
}


