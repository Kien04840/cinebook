package com.cinebook.service;

import com.cinebook.dto.request.BookingFoodItemRequest;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingFoodIntegrationTest {

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
    private FoodItemRepository foodItemRepository;
    @Mock
    private BookingFoodRepository bookingFoodRepository;

    @Spy
    private GenreMapper genreMapper = new GenreMapper();
    @Spy
    private SeatMapper seatMapper = new SeatMapper();
    @Spy
    private PromotionMapper promotionMapper = new PromotionMapper();
    @Spy
    private FoodItemMapper foodItemMapper = new FoodItemMapper();

    private BookingServiceImpl bookingService;

    private User sampleUser;
    private Showtime sampleShowtime;
    private Seat seat1;
    private FoodItem popcorn;
    private FoodItem soda;

    @BeforeEach
    void setUp() {
        MovieMapper movieMapper = new MovieMapper(genreMapper);
        AuditoriumMapper auditoriumMapper = new AuditoriumMapper(seatMapper);
        CinemaMapper cinemaMapper = new CinemaMapper(auditoriumMapper);
        ShowtimeMapper showtimeMapper = new ShowtimeMapper(movieMapper, cinemaMapper, auditoriumMapper);
        BookingMapper bookingMapper = new BookingMapper(showtimeMapper);

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
                null,
                foodItemRepository,
                bookingFoodRepository,
                foodItemMapper
        );

        sampleUser = new User();
        sampleUser.setId("user-1");
        sampleUser.setEmail("customer@cinebook.com");
        sampleUser.setFullName("Nguyen Van A");
        sampleUser.setStatus(UserStatus.ACTIVE);

        UserDetailsImpl userDetails = new UserDetailsImpl(
                sampleUser.getId(),
                sampleUser.getEmail(),
                "password",
                sampleUser.getFullName(),
                sampleUser.getStatus(),
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        Cinema cinema = new Cinema();
        cinema.setId("cin-1");
        cinema.setName("CineBook Central");
        cinema.setStatus(CinemaStatus.ACTIVE);

        Auditorium auditorium = new Auditorium();
        auditorium.setId("aud-1");
        auditorium.setName("Hall 1");
        auditorium.setCinema(cinema);
        auditorium.setStatus(AuditoriumStatus.ACTIVE);

        Movie movie = new Movie();
        movie.setId("mov-1");
        movie.setTitle("Inception");
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setDurationMinutes((short) 148);

        sampleShowtime = new Showtime();
        sampleShowtime.setId("showtime-1");
        sampleShowtime.setMovie(movie);
        sampleShowtime.setAuditorium(auditorium);
        sampleShowtime.setBasePrice(new BigDecimal("100000.00"));
        sampleShowtime.setStartTime(LocalDateTime.now().plusHours(3));
        sampleShowtime.setEndTime(LocalDateTime.now().plusHours(5));
        sampleShowtime.setStatus(ShowtimeStatus.SCHEDULED);

        SeatType standardType = new SeatType();
        standardType.setId("type-standard");
        standardType.setName("STANDARD");
        standardType.setPriceModifier(BigDecimal.ZERO);

        seat1 = new Seat();
        seat1.setId("seat-1");
        seat1.setAuditorium(auditorium);
        seat1.setRowLabel("A");
        seat1.setSeatNumber((short) 1);
        seat1.setSeatType(standardType);
        seat1.setStatus(SeatStatus.ACTIVE);

        popcorn = new FoodItem();
        popcorn.setId("food-popcorn");
        popcorn.setName("Bắp rang bơ phô mai");
        popcorn.setPrice(new BigDecimal("45000"));
        popcorn.setStatus(FoodItemStatus.ACTIVE);

        soda = new FoodItem();
        soda.setId("food-soda");
        soda.setName("Coca-Cola");
        soda.setPrice(new BigDecimal("28000"));
        soda.setStatus(FoodItemStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createBooking with food items should calculate totalAmount = ticketNet + foodTotal and save bookingFoods")
    void createBooking_WithFoodItems_CalculatesTotalAmount() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1"))
                .foodItems(List.of(
                        BookingFoodItemRequest.builder().foodItemId("food-popcorn").quantity(2).build(), // 90,000
                        BookingFoodItemRequest.builder().foodItemId("food-soda").quantity(1).build()     // 28,000
                ))
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());

        when(foodItemRepository.findByIdAndDeletedAtIsNull("food-popcorn")).thenReturn(Optional.of(popcorn));
        when(foodItemRepository.findByIdAndDeletedAtIsNull("food-soda")).thenReturn(Optional.of(soda));

        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId("booking-123");
            b.setCreatedAt(LocalDateTime.now());
            return b;
        });
        when(bookingFoodRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingDetailResponse response = bookingService.createBooking(request);

        assertNotNull(response);
        // ticketGross = 100,000 (1 standard seat)
        // foodTotal = (45,000 * 2) + (28,000 * 1) = 90,000 + 28,000 = 118,000
        // totalAmount = 100,000 + 118,000 = 218,000
        assertEquals(0, new BigDecimal("218000.00").compareTo(response.getTotalAmount()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(response.getGrossAmount()));
        assertEquals(0, new BigDecimal("118000").compareTo(response.getFoodAmount()));
        assertNotNull(response.getFoods());
        assertEquals(2, response.getFoods().size());

        verify(bookingFoodRepository).saveAllAndFlush(any());
        verify(seatHoldRepository).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("createBooking with inactive food item should throw BadRequestException")
    void createBooking_WithInactiveFoodItem_ThrowsException() {
        popcorn.setStatus(FoodItemStatus.INACTIVE);

        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1"))
                .foodItems(List.of(
                        BookingFoodItemRequest.builder().foodItemId("food-popcorn").quantity(1).build()
                ))
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());

        when(foodItemRepository.findByIdAndDeletedAtIsNull("food-popcorn")).thenReturn(Optional.of(popcorn));

        assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createBooking with non-existent food item should throw BadRequestException")
    void createBooking_WithNotFoundFoodItem_ThrowsException() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1"))
                .foodItems(List.of(
                        BookingFoodItemRequest.builder().foodItemId("food-missing").quantity(1).build()
                ))
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(sampleUser));
        when(showtimeRepository.findById("showtime-1")).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findByIdIn(any())).thenReturn(List.of(seat1));
        when(seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(eq("showtime-1"), any(), any()))
                .thenReturn(Collections.emptyList());

        when(foodItemRepository.findByIdAndDeletedAtIsNull("food-missing")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        verify(bookingRepository, never()).saveAndFlush(any());
    }
}
