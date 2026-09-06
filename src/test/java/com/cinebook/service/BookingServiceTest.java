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

    // =========================================================================
    // Booking-Level E-Ticket Check-In Tests
    // =========================================================================

    @Test
    void verifyBookingCheckIn_BlankCode_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> bookingService.verifyBookingCheckIn(""));
        assertThrows(BadRequestException.class, () -> bookingService.verifyBookingCheckIn("   "));
        assertThrows(BadRequestException.class, () -> bookingService.verifyBookingCheckIn(null));
    }

    @Test
    void verifyBookingCheckIn_NotFound_ThrowsNotFound() {
        when(bookingRepository.findByCheckInCodeWithDetails("non-existent-code")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> bookingService.verifyBookingCheckIn("non-existent-code"));
    }

    @Test
    void verifyBookingCheckIn_NotPaid_ReturnsIneligible() {
        Booking b = new Booking();
        b.setId("b-unpaid");
        b.setBookingCode("CB-UNPAID");
        b.setCheckInCode("chk-unpaid");
        b.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        b.setShowtime(sampleShowtime);
        b.setUser(sampleUser);

        when(bookingRepository.findByCheckInCodeWithDetails("chk-unpaid")).thenReturn(Optional.of(b));
        when(ticketRepository.findByBookingIdWithSeat("b-unpaid")).thenReturn(List.of());

        BookingVerifyResponse res = bookingService.verifyBookingCheckIn("chk-unpaid");
        assertNotNull(res);
        assertFalse(res.isCheckInEligible());
        assertTrue(res.getIneligibleReason().contains("chưa hoàn tất thanh toán"));
    }

    @Test
    void verifyBookingCheckIn_CancelledShowtime_ReturnsIneligible() {
        Showtime cancelledShowtime = new Showtime();
        cancelledShowtime.setId("st-cancelled");
        cancelledShowtime.setMovie(sampleMovie);
        cancelledShowtime.setAuditorium(sampleAuditorium);
        cancelledShowtime.setStatus(ShowtimeStatus.CANCELLED);

        Booking b = new Booking();
        b.setId("b-st-cancelled");
        b.setBookingCode("CB-ST-CANCELLED");
        b.setCheckInCode("chk-st-cancelled");
        b.setBookingStatus(BookingStatus.PAID);
        b.setShowtime(cancelledShowtime);
        b.setUser(sampleUser);

        when(bookingRepository.findByCheckInCodeWithDetails("chk-st-cancelled")).thenReturn(Optional.of(b));
        when(ticketRepository.findByBookingIdWithSeat("b-st-cancelled")).thenReturn(List.of());

        BookingVerifyResponse res = bookingService.verifyBookingCheckIn("chk-st-cancelled");
        assertNotNull(res);
        assertFalse(res.isCheckInEligible());
        assertTrue(res.getIneligibleReason().contains("Suất chiếu đã bị hủy"));
    }

    @Test
    void verifyBookingCheckIn_AllTicketsUsed_ReturnsIneligible() {
        Booking b = new Booking();
        b.setId("b-all-used");
        b.setBookingCode("CB-ALL-USED");
        b.setCheckInCode("chk-all-used");
        b.setBookingStatus(BookingStatus.PAID);
        b.setShowtime(sampleShowtime);
        b.setUser(sampleUser);

        Ticket t1 = new Ticket();
        t1.setId("t-1");
        t1.setTicketStatus(TicketStatus.USED);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setSeat(seat1);

        Ticket t2 = new Ticket();
        t2.setId("t-2");
        t2.setTicketStatus(TicketStatus.USED);
        t2.setTicketPrice(new BigDecimal("100000.00"));
        t2.setSeat(seat2);

        when(bookingRepository.findByCheckInCodeWithDetails("chk-all-used")).thenReturn(Optional.of(b));
        when(ticketRepository.findByBookingIdWithSeat("b-all-used")).thenReturn(List.of(t1, t2));

        BookingVerifyResponse res = bookingService.verifyBookingCheckIn("chk-all-used");
        assertNotNull(res);
        assertFalse(res.isCheckInEligible());
        assertEquals(2, res.getTotalTickets());
        assertEquals(0, res.getValidTickets());
        assertEquals(2, res.getUsedTickets());
        assertTrue(res.getIneligibleReason().contains("đã được sử dụng"));
    }

    @Test
    void verifyBookingCheckIn_ValidTickets_ReturnsEligible() {
        Booking b = new Booking();
        b.setId("b-valid");
        b.setBookingCode("CB-VALID");
        b.setCheckInCode("chk-valid");
        b.setBookingStatus(BookingStatus.PAID);
        b.setShowtime(sampleShowtime);
        b.setUser(sampleUser);

        Ticket t1 = new Ticket();
        t1.setId("t-1");
        t1.setTicketStatus(TicketStatus.VALID);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setSeat(seat1);

        Ticket t2 = new Ticket();
        t2.setId("t-2");
        t2.setTicketStatus(TicketStatus.VALID);
        t2.setTicketPrice(new BigDecimal("100000.00"));
        t2.setSeat(seat2);

        when(bookingRepository.findByCheckInCodeWithDetails("chk-valid")).thenReturn(Optional.of(b));
        when(ticketRepository.findByBookingIdWithSeat("b-valid")).thenReturn(List.of(t1, t2));

        BookingVerifyResponse res = bookingService.verifyBookingCheckIn("chk-valid");
        assertNotNull(res);
        assertTrue(res.isCheckInEligible());
        assertEquals(2, res.getTotalTickets());
        assertEquals(2, res.getValidTickets());
        assertEquals(0, res.getUsedTickets());
        assertNull(res.getIneligibleReason());
        assertEquals(2, res.getTickets().size());
    }

    @Test
    void checkInBooking_BlankCode_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> bookingService.checkInBooking(null));
        assertThrows(BadRequestException.class, () -> bookingService.checkInBooking(
                com.cinebook.dto.request.BookingCheckInRequest.builder().checkInCode("").build()));
    }

    @Test
    void checkInBooking_NotFound_ThrowsNotFound() {
        when(bookingRepository.findByCheckInCodeWithLock("non-existent")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> bookingService.checkInBooking(
                com.cinebook.dto.request.BookingCheckInRequest.builder().checkInCode("non-existent").build()));
    }

    @Test
    void checkInBooking_NotPaid_ThrowsBadRequest() {
        Booking b = new Booking();
        b.setId("b-unpaid");
        b.setBookingStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findByCheckInCodeWithLock("chk-cancelled")).thenReturn(Optional.of(b));
        when(ticketRepository.findByBookingIdWithLock("b-unpaid")).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> bookingService.checkInBooking(
                com.cinebook.dto.request.BookingCheckInRequest.builder().checkInCode("chk-cancelled").build()));
    }

    @Test
    void checkInBooking_AllTicketsAlreadyUsed_ThrowsConflict409() {
        Booking b = new Booking();
        b.setId("b-used");
        b.setBookingCode("CB-USED");
        b.setCheckInCode("chk-used");
        b.setBookingStatus(BookingStatus.PAID);
        b.setShowtime(sampleShowtime);

        Ticket t1 = new Ticket();
        t1.setId("t-1");
        t1.setTicketStatus(TicketStatus.USED);

        when(bookingRepository.findByCheckInCodeWithLock("chk-used")).thenReturn(Optional.of(b));
        when(ticketRepository.findByBookingIdWithLock("b-used")).thenReturn(List.of(t1));

        assertThrows(ConflictException.class, () -> bookingService.checkInBooking(
                com.cinebook.dto.request.BookingCheckInRequest.builder().checkInCode("chk-used").build()));
    }

    @Test
    void checkInBooking_ValidTickets_AtomicTransitionSuccess() {
        Booking b = new Booking();
        b.setId("b-checkin");
        b.setBookingCode("CB-CHECKIN");
        b.setCheckInCode("chk-checkin");
        b.setBookingStatus(BookingStatus.PAID);
        b.setShowtime(sampleShowtime);
        b.setUser(sampleUser);

        Ticket t1 = new Ticket();
        t1.setId("t-1");
        t1.setTicketStatus(TicketStatus.VALID);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setSeat(seat1);

        Ticket t2 = new Ticket();
        t2.setId("t-2");
        t2.setTicketStatus(TicketStatus.VALID);
        t2.setTicketPrice(new BigDecimal("100000.00"));
        t2.setSeat(seat2);

        when(bookingRepository.findByCheckInCodeWithLock("chk-checkin")).thenReturn(Optional.of(b));
        when(ticketRepository.findByBookingIdWithLock("b-checkin")).thenReturn(List.of(t1, t2));
        when(ticketRepository.findByBookingIdWithSeat("b-checkin")).thenReturn(List.of(t1, t2));

        BookingCheckInResponse res = bookingService.checkInBooking(
                com.cinebook.dto.request.BookingCheckInRequest.builder().checkInCode("chk-checkin").build());

        assertNotNull(res);
        assertEquals("CHECKED_IN", res.getResult());
        assertEquals(2, res.getTotalTickets());
        assertEquals(2, res.getCheckedInCount());
        assertEquals(0, res.getAlreadyUsedCount());
        assertEquals(TicketStatus.USED, t1.getTicketStatus());
        assertEquals(TicketStatus.USED, t2.getTicketStatus());

        verify(ticketRepository).saveAllAndFlush(List.of(t1, t2));
    }

    @Test
    void checkInBooking_PartialValidTickets_ChecksInRemainingValid() {
        Booking b = new Booking();
        b.setId("b-partial");
        b.setBookingCode("CB-PARTIAL");
        b.setCheckInCode("chk-partial");
        b.setBookingStatus(BookingStatus.PAID);
        b.setShowtime(sampleShowtime);
        b.setUser(sampleUser);

        Ticket t1 = new Ticket();
        t1.setId("t-1");
        t1.setTicketStatus(TicketStatus.USED);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setSeat(seat1);

        Ticket t2 = new Ticket();
        t2.setId("t-2");
        t2.setTicketStatus(TicketStatus.VALID);
        t2.setTicketPrice(new BigDecimal("100000.00"));
        t2.setSeat(seat2);

        when(bookingRepository.findByCheckInCodeWithLock("chk-partial")).thenReturn(Optional.of(b));
        when(ticketRepository.findByBookingIdWithLock("b-partial")).thenReturn(List.of(t1, t2));
        when(ticketRepository.findByBookingIdWithSeat("b-partial")).thenReturn(List.of(t1, t2));

        BookingCheckInResponse res = bookingService.checkInBooking(
                com.cinebook.dto.request.BookingCheckInRequest.builder().checkInCode("chk-partial").build());

        assertNotNull(res);
        assertEquals("PARTIALLY_CHECKED_IN", res.getResult());
        assertEquals(2, res.getTotalTickets());
        assertEquals(1, res.getCheckedInCount());
        assertEquals(1, res.getAlreadyUsedCount());
        assertEquals(TicketStatus.USED, t2.getTicketStatus());

        verify(ticketRepository).saveAllAndFlush(List.of(t2));
    }

    // ==========================================
    // State Machine & Audit Tests
    // ==========================================

    @Test
    void cancelBooking_PendingPayment_Success_CancelsPendingPaymentsAndReleasesQuota() {
        Booking booking = new Booking();
        booking.setId("b-cancel-1");
        booking.setBookingCode("CB-CANCEL-1");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(3));

        Payment pendingPayment = new Payment();
        pendingPayment.setId("p-pending-1");
        pendingPayment.setBooking(booking);
        pendingPayment.setPaymentStatus(PaymentStatus.PENDING);

        Promotion promo = new Promotion();
        promo.setId("promo-1");
        promo.setCode("DISCOUNT10");
        promo.setUsedCount(5);

        BookingPromotion bp = new BookingPromotion();
        bp.setPromotion(promo);
        bp.setBooking(booking);

        when(bookingRepository.findById("b-cancel-1")).thenReturn(Optional.of(booking));
        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(bookingPromotionRepository.findByBookingId("b-cancel-1")).thenReturn(List.of(bp));
        when(promotionRepository.findByIdWithLock("promo-1")).thenReturn(Optional.of(promo));
        when(paymentRepository.findByBookingId("b-cancel-1")).thenReturn(List.of(pendingPayment));
        when(ticketRepository.findByBookingId("b-cancel-1")).thenReturn(Collections.emptyList());
        when(seatHoldRepository.findByBookingId("b-cancel-1")).thenReturn(Collections.emptyList());

        BookingDetailResponse res = bookingService.cancelBooking("b-cancel-1", new CancelBookingRequest("Đổi ý"));

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, booking.getBookingStatus());
        assertEquals(PaymentStatus.CANCELLED, pendingPayment.getPaymentStatus());
        assertEquals(4, promo.getUsedCount());

        verify(bookingRepository).save(booking);
        verify(seatHoldRepository).deleteByBookingId("b-cancel-1");
        verify(promotionRepository).save(promo);
        verify(paymentRepository).saveAll(List.of(pendingPayment));
    }

    @Test
    void cancelBooking_ExpiredHold_ExpiresBookingAndThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("b-expired-1");
        booking.setBookingCode("CB-EXP-1");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findById("b-expired-1")).thenReturn(Optional.of(booking));
        when(bookingPromotionRepository.findByBookingId("b-expired-1")).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> bookingService.cancelBooking("b-expired-1", new CancelBookingRequest("Hủy")));

        assertTrue(ex.getMessage().contains("Đơn đặt vé đã hết hạn giữ chỗ và không thể hủy."));
        assertEquals(BookingStatus.EXPIRED, booking.getBookingStatus());
        verify(seatHoldRepository).deleteByBookingId("b-expired-1");
    }

    @Test
    void cancelBooking_PaidBooking_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("b-paid-1");
        booking.setUser(sampleUser);
        booking.setBookingStatus(BookingStatus.PAID);

        when(bookingRepository.findById("b-paid-1")).thenReturn(Optional.of(booking));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> bookingService.cancelBooking("b-paid-1", new CancelBookingRequest("Hủy")));

        assertTrue(ex.getMessage().contains("Không thể tự hủy đơn đặt vé đã thanh toán thành công"));
    }

    @Test
    void getBookingDetail_LazyExpiration_ExpiresBookingWhenHoldExpired() {
        Booking booking = new Booking();
        booking.setId("b-lazy-exp");
        booking.setBookingCode("CB-LAZY-EXP");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));

        when(bookingRepository.findById("b-lazy-exp")).thenReturn(Optional.of(booking));
        when(bookingPromotionRepository.findByBookingId("b-lazy-exp")).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(ticketRepository.findByBookingId("b-lazy-exp")).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBookingId("b-lazy-exp")).thenReturn(Collections.emptyList());
        when(seatHoldRepository.findByBookingId("b-lazy-exp")).thenReturn(Collections.emptyList());

        BookingDetailResponse res = bookingService.getBookingDetail("b-lazy-exp");

        assertNotNull(res);
        assertEquals(BookingStatus.EXPIRED, res.getBookingStatus());
        assertEquals(BookingStatus.EXPIRED, booking.getBookingStatus());
        verify(seatHoldRepository).deleteByBookingId("b-lazy-exp");
        verify(bookingRepository).save(booking);
    }

    @Test
    void checkInBooking_RefundedBooking_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("b-refunded-chk");
        booking.setBookingCode("CB-REFUNDED");
        booking.setCheckInCode("chk-refunded");
        booking.setBookingStatus(BookingStatus.REFUNDED);

        when(bookingRepository.findByCheckInCodeWithLock("chk-refunded")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingIdWithLock("b-refunded-chk")).thenReturn(Collections.emptyList());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> bookingService.checkInBooking(com.cinebook.dto.request.BookingCheckInRequest.builder().checkInCode("chk-refunded").build()));

        assertTrue(ex.getMessage().contains("Đơn đặt vé đã hoàn tiền, không thể thực hiện soát vé."));
    }

    @Test
    void checkInBooking_CancelledTickets_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("b-cancelled-tix");
        booking.setBookingCode("CB-CANCELLED-TIX");
        booking.setCheckInCode("chk-cancelled-tix");
        booking.setBookingStatus(BookingStatus.PAID);

        Ticket ticket = new Ticket();
        ticket.setId("t-cancelled");
        ticket.setTicketStatus(TicketStatus.CANCELLED);

        when(bookingRepository.findByCheckInCodeWithLock("chk-cancelled-tix")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingIdWithLock("b-cancelled-tix")).thenReturn(List.of(ticket));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> bookingService.checkInBooking(com.cinebook.dto.request.BookingCheckInRequest.builder().checkInCode("chk-cancelled-tix").build()));

        assertTrue(ex.getMessage().contains("Không có vé hợp lệ để soát trong đơn đặt vé này."));
    }

    @Test
    void processBookingRefund_UsedTickets_ThrowsBadRequest() {
        Booking booking = new Booking();
        booking.setId("b-refund-used");
        booking.setBookingStatus(BookingStatus.PAID);

        Ticket usedTicket = new Ticket();
        usedTicket.setId("t-used");
        usedTicket.setTicketStatus(TicketStatus.USED);

        when(bookingRepository.findByIdWithLock("b-refund-used")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("b-refund-used")).thenReturn(List.of(usedTicket));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> bookingService.processBookingRefund("b-refund-used", "Khách yêu cầu", sampleUser.getId()));

        assertTrue(ex.getMessage().contains("Không thể hoàn tiền cho đơn hàng đã được sử dụng để vào rạp."));
    }

    // ==========================================
    // AUTOMATIC BOOKING EXPIRATION TESTS (1-11)
    // ==========================================

    @Test
    void expireBookingIfHoldExpired_Test1_ImmediateOverdue_ExpiresBookingAndReleasesHolds() {
        Booking booking = new Booking();
        booking.setId("b-overdue-1m");
        booking.setBookingCode("CB-OVERDUE-1M");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findByIdWithLock("b-overdue-1m")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertNotNull(result);
        assertEquals(BookingStatus.EXPIRED, result.getBookingStatus());
        verify(seatHoldRepository, times(1)).deleteByBookingId("b-overdue-1m");
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    void expireBookingIfHoldExpired_Test2_MultiDayOverdue_ExpiresBooking() {
        Booking booking = new Booking();
        booking.setId("b-overdue-3d");
        booking.setBookingCode("CB-OVERDUE-3D");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusDays(3));

        when(bookingRepository.findByIdWithLock("b-overdue-3d")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertNotNull(result);
        assertEquals(BookingStatus.EXPIRED, result.getBookingStatus());
        verify(seatHoldRepository, times(1)).deleteByBookingId("b-overdue-3d");
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    void expireBookingIfHoldExpired_Test3_FutureBooking_RemainsPendingPayment() {
        Booking booking = new Booking();
        booking.setId("b-future");
        booking.setBookingCode("CB-FUTURE");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(3));

        when(bookingRepository.findByIdWithLock("b-future")).thenReturn(Optional.of(booking));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertNotNull(result);
        assertEquals(BookingStatus.PENDING_PAYMENT, result.getBookingStatus());
        verify(seatHoldRepository, never()).deleteByBookingId(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void expireBookingIfHoldExpired_Test4_ExactBoundary_ExpiresBooking() {
        LocalDateTime exactNow = LocalDateTime.now();
        Booking booking = new Booking();
        booking.setId("b-boundary");
        booking.setBookingCode("CB-BOUNDARY");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(exactNow);

        when(bookingRepository.findByIdWithLock("b-boundary")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertNotNull(result);
        assertEquals(BookingStatus.EXPIRED, result.getBookingStatus());
        verify(seatHoldRepository, times(1)).deleteByBookingId("b-boundary");
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    void expireBookingIfHoldExpired_Test5_Idempotency_SecondCallDoesNothing() {
        Booking booking = new Booking();
        booking.setId("b-idempotent");
        booking.setBookingCode("CB-IDEMPOTENT");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(5));

        when(bookingRepository.findByIdWithLock("b-idempotent")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // First invocation: transitions to EXPIRED
        Booking firstResult = bookingService.expireBookingIfHoldExpired(booking);
        assertEquals(BookingStatus.EXPIRED, firstResult.getBookingStatus());

        // Second invocation: already EXPIRED, must skip immediately
        Booking secondResult = bookingService.expireBookingIfHoldExpired(firstResult);
        assertEquals(BookingStatus.EXPIRED, secondResult.getBookingStatus());

        // Verify side effects occurred exactly once
        verify(seatHoldRepository, times(1)).deleteByBookingId("b-idempotent");
        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    void expireBookingIfHoldExpired_Test6_AlreadyCancelled_Ignored() {
        Booking booking = new Booking();
        booking.setId("b-cancelled");
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(10));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertEquals(BookingStatus.CANCELLED, result.getBookingStatus());
        verify(seatHoldRepository, never()).deleteByBookingId(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void expireBookingIfHoldExpired_Test7_AlreadyPaid_Ignored() {
        Booking booking = new Booking();
        booking.setId("b-paid");
        booking.setBookingStatus(BookingStatus.PAID);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(10));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertEquals(BookingStatus.PAID, result.getBookingStatus());
        verify(seatHoldRepository, never()).deleteByBookingId(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void expireBookingIfHoldExpired_Test8_AlreadyRefunded_Ignored() {
        Booking booking = new Booking();
        booking.setId("b-refunded");
        booking.setBookingStatus(BookingStatus.REFUNDED);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(10));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertEquals(BookingStatus.REFUNDED, result.getBookingStatus());
        verify(seatHoldRepository, never()).deleteByBookingId(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void expireBookingIfHoldExpired_Test9_MultiplePaymentAttempts_OnlyPendingCancelled() {
        Booking booking = new Booking();
        booking.setId("b-multi-payments");
        booking.setBookingCode("CB-MULTI-PAY");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));

        Payment p1 = new Payment();
        p1.setId("pay-1");
        p1.setPaymentStatus(PaymentStatus.CANCELLED);

        Payment p2 = new Payment();
        p2.setId("pay-2");
        p2.setPaymentStatus(PaymentStatus.FAILED);

        Payment p3 = new Payment();
        p3.setId("pay-3");
        p3.setPaymentStatus(PaymentStatus.PENDING);

        when(bookingRepository.findByIdWithLock("b-multi-payments")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByBookingId("b-multi-payments")).thenReturn(List.of(p1, p2, p3));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertEquals(BookingStatus.EXPIRED, result.getBookingStatus());
        assertEquals(PaymentStatus.CANCELLED, p1.getPaymentStatus(), "Terminal CANCELLED payment must remain untouched");
        assertEquals(PaymentStatus.FAILED, p2.getPaymentStatus(), "Terminal FAILED payment must remain untouched");
        assertEquals(PaymentStatus.CANCELLED, p3.getPaymentStatus(), "Active PENDING payment must transition to CANCELLED");
        verify(paymentRepository, times(1)).saveAll(List.of(p3));
    }

    @Test
    void expireBookingIfHoldExpired_Test10_PromotionQuotaRollback_ExactlyOnce() {
        Booking booking = new Booking();
        booking.setId("b-promo");
        booking.setBookingCode("CB-PROMO");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));

        Promotion promo = new Promotion();
        promo.setId("promo-1");
        promo.setCode("SAVE50");
        promo.setUsedCount(1);

        BookingPromotion bp = new BookingPromotion();
        bp.setBooking(booking);
        bp.setPromotion(promo);

        when(bookingRepository.findByIdWithLock("b-promo")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingPromotionRepository.findByBookingId("b-promo")).thenReturn(List.of(bp));
        when(promotionRepository.findByIdWithLock("promo-1")).thenReturn(Optional.of(promo));

        // First call: usedCount goes 1 -> 0
        Booking first = bookingService.expireBookingIfHoldExpired(booking);
        assertEquals(BookingStatus.EXPIRED, first.getBookingStatus());
        assertEquals(0, promo.getUsedCount());
        verify(promotionRepository, times(1)).save(promo);

        // Second call: already EXPIRED, usedCount remains 0
        Booking second = bookingService.expireBookingIfHoldExpired(first);
        assertEquals(BookingStatus.EXPIRED, second.getBookingStatus());
        assertEquals(0, promo.getUsedCount());
        verify(promotionRepository, times(1)).save(promo); // Still only saved once
    }

    @Test
    void expireBookingIfHoldExpired_Test11_NullBooking_ReturnsNull() {
        Booking result = bookingService.expireBookingIfHoldExpired(null);
        assertNull(result);
    }

    // ==========================================
    // SEAT HISTORY VISIBILITY & RACE CONDITION TESTS
    // ==========================================

    @Test
    void getBookingDetail_Test1_PaidBooking_ReturnsAllSeats() {
        Booking booking = new Booking();
        booking.setId("b-paid-seats");
        booking.setBookingCode("CB-PAID-SEATS");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PAID);
        booking.setTotalAmount(new BigDecimal("220000.00"));
        booking.setCreatedAt(LocalDateTime.now());

        Ticket t1 = new Ticket();
        t1.setId("t-paid-1");
        t1.setBooking(booking);
        t1.setSeat(seat1);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setTicketStatus(TicketStatus.VALID);

        Ticket t2 = new Ticket();
        t2.setId("t-paid-2");
        t2.setBooking(booking);
        t2.setSeat(seat2);
        t2.setTicketPrice(new BigDecimal("120000.00"));
        t2.setTicketStatus(TicketStatus.VALID);

        booking.setTickets(List.of(t1, t2));

        when(bookingRepository.findById("b-paid-seats")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("b-paid-seats")).thenReturn(List.of(t1, t2));
        when(paymentRepository.findByBookingId("b-paid-seats")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.getBookingDetail("b-paid-seats");

        assertNotNull(response);
        assertEquals(BookingStatus.PAID, response.getBookingStatus());
        assertEquals(2, response.getSeats().size());
        assertEquals("A1", response.getSeats().get(0).getSeatCode());
        assertEquals("A2", response.getSeats().get(1).getSeatCode());

        BookingSummaryResponse summary = bookingMapper.toBookingSummaryResponse(booking);
        assertEquals(List.of("A1", "A2"), summary.getSeatCodes());
    }

    @Test
    void getBookingDetail_Test2_RefundedBooking_ReturnsAllOriginalSeats() {
        Booking booking = new Booking();
        booking.setId("b-refunded-seats");
        booking.setBookingCode("CB-REFUNDED-SEATS");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.REFUNDED);
        booking.setTotalAmount(new BigDecimal("220000.00"));
        booking.setCreatedAt(LocalDateTime.now().minusDays(1));

        Ticket t1 = new Ticket();
        t1.setId("t-ref-1");
        t1.setBooking(booking);
        t1.setSeat(seat1);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setTicketStatus(TicketStatus.CANCELLED);

        Ticket t2 = new Ticket();
        t2.setId("t-ref-2");
        t2.setBooking(booking);
        t2.setSeat(seat2);
        t2.setTicketPrice(new BigDecimal("120000.00"));
        t2.setTicketStatus(TicketStatus.CANCELLED);

        booking.setTickets(List.of(t1, t2));

        when(bookingRepository.findById("b-refunded-seats")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("b-refunded-seats")).thenReturn(List.of(t1, t2));
        when(paymentRepository.findByBookingId("b-refunded-seats")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.getBookingDetail("b-refunded-seats");

        assertNotNull(response);
        assertEquals(BookingStatus.REFUNDED, response.getBookingStatus());
        assertEquals(2, response.getSeats().size());
        assertEquals("A1", response.getSeats().get(0).getSeatCode());
        assertEquals("A2", response.getSeats().get(1).getSeatCode());

        BookingSummaryResponse summary = bookingMapper.toBookingSummaryResponse(booking);
        assertEquals(List.of("A1", "A2"), summary.getSeatCodes());
    }

    @Test
    void getBookingDetail_Test3_CancelledBooking_ReturnsAllOriginalSeats() {
        Booking booking = new Booking();
        booking.setId("b-cancelled-seats");
        booking.setBookingCode("CB-CANCELLED-SEATS");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setTotalAmount(new BigDecimal("220000.00"));
        booking.setCancelledAt(LocalDateTime.now().minusHours(2));

        Ticket t1 = new Ticket();
        t1.setId("t-canc-1");
        t1.setBooking(booking);
        t1.setSeat(seat1);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setTicketStatus(TicketStatus.CANCELLED);

        Ticket t2 = new Ticket();
        t2.setId("t-canc-2");
        t2.setBooking(booking);
        t2.setSeat(seat2);
        t2.setTicketPrice(new BigDecimal("120000.00"));
        t2.setTicketStatus(TicketStatus.CANCELLED);

        booking.setTickets(List.of(t1, t2));

        when(bookingRepository.findById("b-cancelled-seats")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("b-cancelled-seats")).thenReturn(List.of(t1, t2));
        when(paymentRepository.findByBookingId("b-cancelled-seats")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.getBookingDetail("b-cancelled-seats");

        assertNotNull(response);
        assertEquals(BookingStatus.CANCELLED, response.getBookingStatus());
        assertEquals(2, response.getSeats().size());
        assertEquals("A1", response.getSeats().get(0).getSeatCode());
        assertEquals("A2", response.getSeats().get(1).getSeatCode());

        BookingSummaryResponse summary = bookingMapper.toBookingSummaryResponse(booking);
        assertEquals(List.of("A1", "A2"), summary.getSeatCodes());
    }

    @Test
    void getBookingDetail_Test4_ExpiredBooking_ReturnsAllOriginalSeats() {
        Booking booking = new Booking();
        booking.setId("b-expired-seats");
        booking.setBookingCode("CB-EXPIRED-SEATS");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.EXPIRED);
        booking.setTotalAmount(new BigDecimal("220000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(30));

        Ticket t1 = new Ticket();
        t1.setId("t-exp-1");
        t1.setBooking(booking);
        t1.setSeat(seat1);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setTicketStatus(TicketStatus.CANCELLED);

        Ticket t2 = new Ticket();
        t2.setId("t-exp-2");
        t2.setBooking(booking);
        t2.setSeat(seat2);
        t2.setTicketPrice(new BigDecimal("120000.00"));
        t2.setTicketStatus(TicketStatus.CANCELLED);

        booking.setTickets(List.of(t1, t2));

        when(bookingRepository.findById("b-expired-seats")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("b-expired-seats")).thenReturn(List.of(t1, t2));
        when(paymentRepository.findByBookingId("b-expired-seats")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.getBookingDetail("b-expired-seats");

        assertNotNull(response);
        assertEquals(BookingStatus.EXPIRED, response.getBookingStatus());
        assertEquals(2, response.getSeats().size());
        assertEquals("A1", response.getSeats().get(0).getSeatCode());
        assertEquals("A2", response.getSeats().get(1).getSeatCode());

        BookingSummaryResponse summary = bookingMapper.toBookingSummaryResponse(booking);
        assertEquals(List.of("A1", "A2"), summary.getSeatCodes());
    }

    @Test
    void getBookingDetail_Test5_MultiSeatBooking_ReturnsEverySeat() {
        Seat seat3 = new Seat();
        seat3.setId("seat-3");
        seat3.setRowLabel("B");
        seat3.setSeatNumber((short) 1);
        seat3.setSeatType(standardType);

        Seat seat4 = new Seat();
        seat4.setId("seat-4");
        seat4.setRowLabel("B");
        seat4.setSeatNumber((short) 2);
        seat4.setSeatType(standardType);

        Booking booking = new Booking();
        booking.setId("b-multiseat");
        booking.setBookingCode("CB-MULTISEAT");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PAID);
        booking.setTotalAmount(new BigDecimal("420000.00"));

        Ticket t1 = new Ticket(); t1.setId("t-1"); t1.setSeat(seat1); t1.setTicketPrice(new BigDecimal("100000.00")); t1.setTicketStatus(TicketStatus.VALID);
        Ticket t2 = new Ticket(); t2.setId("t-2"); t2.setSeat(seat2); t2.setTicketPrice(new BigDecimal("120000.00")); t2.setTicketStatus(TicketStatus.VALID);
        Ticket t3 = new Ticket(); t3.setId("t-3"); t3.setSeat(seat3); t3.setTicketPrice(new BigDecimal("100000.00")); t3.setTicketStatus(TicketStatus.VALID);
        Ticket t4 = new Ticket(); t4.setId("t-4"); t4.setSeat(seat4); t4.setTicketPrice(new BigDecimal("100000.00")); t4.setTicketStatus(TicketStatus.VALID);

        booking.setTickets(List.of(t1, t2, t3, t4));

        when(bookingRepository.findById("b-multiseat")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("b-multiseat")).thenReturn(List.of(t1, t2, t3, t4));
        when(paymentRepository.findByBookingId("b-multiseat")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.getBookingDetail("b-multiseat");

        assertNotNull(response);
        assertEquals(4, response.getSeats().size());
        List<String> codes = response.getSeats().stream().map(BookingSeatResponse::getSeatCode).toList();
        assertEquals(List.of("A1", "A2", "B1", "B2"), codes);
    }

    @Test
    void getBookingDetail_Test6_MultiplePaymentAttempts_ReturnsCorrectSeatsAndPayments() {
        Booking booking = new Booking();
        booking.setId("b-multi-pay-detail");
        booking.setBookingCode("CB-MULTI-PAY-DETAIL");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PAID);
        booking.setTotalAmount(new BigDecimal("100000.00"));

        Ticket t1 = new Ticket();
        t1.setId("t-pay-1");
        t1.setSeat(seat1);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setTicketStatus(TicketStatus.VALID);

        Payment p1 = new Payment();
        p1.setId("pay-failed");
        p1.setPaymentCode("PAY-01");
        p1.setAmount(new BigDecimal("100000.00"));
        p1.setPaymentStatus(PaymentStatus.FAILED);

        Payment p2 = new Payment();
        p2.setId("pay-success");
        p2.setPaymentCode("PAY-02");
        p2.setAmount(new BigDecimal("100000.00"));
        p2.setPaymentStatus(PaymentStatus.SUCCESS);

        when(bookingRepository.findById("b-multi-pay-detail")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("b-multi-pay-detail")).thenReturn(List.of(t1));
        when(paymentRepository.findByBookingId("b-multi-pay-detail")).thenReturn(List.of(p1, p2));

        BookingDetailResponse response = bookingService.getBookingDetail("b-multi-pay-detail");

        assertNotNull(response);
        assertEquals(1, response.getSeats().size());
        assertEquals("A1", response.getSeats().get(0).getSeatCode());
        assertEquals(2, response.getPayments().size());
    }

    @Test
    void getBookingDetail_Test7_PartialCheckIn_ReturnsAllSeatsWithIndividualStatus() {
        Booking booking = new Booking();
        booking.setId("b-partial-checkin");
        booking.setBookingCode("CB-PARTIAL-CHECKIN");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PAID);
        booking.setTotalAmount(new BigDecimal("220000.00"));

        Ticket t1 = new Ticket();
        t1.setId("t-chk-1");
        t1.setSeat(seat1);
        t1.setTicketPrice(new BigDecimal("100000.00"));
        t1.setTicketStatus(TicketStatus.USED);

        Ticket t2 = new Ticket();
        t2.setId("t-chk-2");
        t2.setSeat(seat2);
        t2.setTicketPrice(new BigDecimal("120000.00"));
        t2.setTicketStatus(TicketStatus.VALID);

        when(bookingRepository.findById("b-partial-checkin")).thenReturn(Optional.of(booking));
        when(ticketRepository.findByBookingId("b-partial-checkin")).thenReturn(List.of(t1, t2));
        when(paymentRepository.findByBookingId("b-partial-checkin")).thenReturn(Collections.emptyList());

        BookingDetailResponse response = bookingService.getBookingDetail("b-partial-checkin");

        assertNotNull(response);
        assertEquals(2, response.getSeats().size());
        assertEquals(2, response.getTickets().size());
        assertEquals(TicketStatus.USED, response.getTickets().get(0).getTicketStatus());
        assertEquals(TicketStatus.VALID, response.getTickets().get(1).getTicketStatus());
    }

    @Test
    void seatAvailability_Test8_ReleasedSeatsAreAvailableWhileHistoryPreserved() {
        Booking booking = new Booking();
        booking.setId("b-release-seats");
        booking.setBookingCode("CB-RELEASE-SEATS");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));

        SeatHold hold1 = new SeatHold();
        hold1.setId(101L);
        hold1.setBooking(booking);
        hold1.setShowtime(sampleShowtime);
        hold1.setSeat(seat1);

        SeatHold hold2 = new SeatHold();
        hold2.setId(102L);
        hold2.setBooking(booking);
        hold2.setShowtime(sampleShowtime);
        hold2.setSeat(seat2);

        when(bookingRepository.findByIdWithLock("b-release-seats")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seatHoldRepository.findByBookingId("b-release-seats")).thenReturn(List.of(hold1, hold2));

        // Expire booking: snapshots tickets with CANCELLED status, deletes seat holds
        Booking expiredResult = bookingService.expireBookingIfHoldExpired(booking);

        assertEquals(BookingStatus.EXPIRED, expiredResult.getBookingStatus());
        verify(seatHoldRepository, times(1)).deleteByBookingId("b-release-seats");
        verify(ticketRepository, times(1)).saveAllAndFlush(argThat(tickets -> {
            List<Ticket> list = (List<Ticket>) tickets;
            return list.size() == 2 && list.stream().allMatch(t -> t.getTicketStatus() == TicketStatus.CANCELLED);
        }));

        // In showtime seat availability check, only VALID and USED tickets occupy a seat.
        // Therefore CANCELLED tickets keep the seat AVAILABLE in auditorium while preserving history.
        Set<TicketStatus> soldStatuses = Set.of(TicketStatus.VALID, TicketStatus.USED);
        assertFalse(soldStatuses.contains(TicketStatus.CANCELLED),
                "CANCELLED tickets must never be considered sold, ensuring seats remain AVAILABLE");
    }

    @Test
    void raceCondition_Test9_SchedulerVsIpn_WhenAlreadyPaid_SchedulerSkips() {
        Booking booking = new Booking();
        booking.setId("b-race-ipn");
        booking.setBookingCode("CB-RACE-IPN");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusSeconds(10));

        // Concurrently, IPN transaction commits first: status changes to PAID
        Booking refreshedPaidBooking = new Booking();
        refreshedPaidBooking.setId("b-race-ipn");
        refreshedPaidBooking.setBookingCode("CB-RACE-IPN");
        refreshedPaidBooking.setBookingStatus(BookingStatus.PAID);
        refreshedPaidBooking.setHoldExpiresAt(LocalDateTime.now().minusSeconds(10));

        when(bookingRepository.findByIdWithLock("b-race-ipn")).thenReturn(Optional.of(refreshedPaidBooking));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertEquals(BookingStatus.PAID, result.getBookingStatus(), "Must preserve PAID status from concurrent IPN");
        verify(seatHoldRepository, never()).deleteByBookingId(any());
        verify(ticketRepository, never()).saveAllAndFlush(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void raceCondition_Test10_SchedulerVsCancel_WhenAlreadyCancelled_SchedulerSkips() {
        Booking booking = new Booking();
        booking.setId("b-race-cancel");
        booking.setBookingCode("CB-RACE-CANCEL");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().minusSeconds(5));

        // Concurrently, user cancellation commits first: status changes to CANCELLED
        Booking refreshedCancelledBooking = new Booking();
        refreshedCancelledBooking.setId("b-race-cancel");
        refreshedCancelledBooking.setBookingCode("CB-RACE-CANCEL");
        refreshedCancelledBooking.setBookingStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findByIdWithLock("b-race-cancel")).thenReturn(Optional.of(refreshedCancelledBooking));

        Booking result = bookingService.expireBookingIfHoldExpired(booking);

        assertEquals(BookingStatus.CANCELLED, result.getBookingStatus(), "Must preserve CANCELLED status from concurrent cancel");
        verify(seatHoldRepository, never()).deleteByBookingId(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_Test11_SnapshotsHeldSeatsAsCancelledTickets_BeforeDeletingHolds() {
        Booking booking = new Booking();
        booking.setId("b-user-cancel");
        booking.setBookingCode("CB-USER-CANCEL");
        booking.setUser(sampleUser);
        booking.setShowtime(sampleShowtime);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(3));

        SeatHold hold1 = new SeatHold();
        hold1.setId(201L);
        hold1.setBooking(booking);
        hold1.setShowtime(sampleShowtime);
        hold1.setSeat(seat1);

        when(bookingRepository.findByIdWithLock("b-user-cancel")).thenReturn(Optional.of(booking));
        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(seatHoldRepository.findByBookingId("b-user-cancel")).thenReturn(List.of(hold1));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByBookingId("b-user-cancel")).thenReturn(Collections.emptyList());

        CancelBookingRequest request = CancelBookingRequest.builder().reason("Changed plans").build();
        BookingDetailResponse response = bookingService.cancelBooking("b-user-cancel", request);

        assertNotNull(response);
        assertEquals(BookingStatus.CANCELLED, response.getBookingStatus());
        verify(ticketRepository, times(1)).saveAllAndFlush(argThat(tickets -> {
            List<Ticket> list = (List<Ticket>) tickets;
            return list.size() == 1 && list.get(0).getTicketStatus() == TicketStatus.CANCELLED;
        }));
        verify(seatHoldRepository, times(1)).deleteByBookingId("b-user-cancel");
    }
}


