package com.cinebook.service;

import com.cinebook.dto.request.CreateShowtimeRequest;
import com.cinebook.dto.request.UpdateShowtimeRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.ShowtimeDetailResponse;
import com.cinebook.dto.response.ShowtimeSummaryResponse;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.enums.MovieStatus;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.AuditoriumMapper;
import com.cinebook.mapper.CinemaMapper;
import com.cinebook.mapper.GenreMapper;
import com.cinebook.mapper.MovieMapper;
import com.cinebook.mapper.SeatMapper;
import com.cinebook.mapper.ShowtimeMapper;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.CinemaRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.service.impl.ShowtimeServiceImpl;
import com.cinebook.service.scheduling.SchedulingValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowtimeServiceTest {

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private AuditoriumRepository auditoriumRepository;

    @Mock
    private CinemaRepository cinemaRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Spy
    private SchedulingValidationService validationService = new SchedulingValidationService();

    @Spy
    private ShowtimeMapper showtimeMapper = new ShowtimeMapper(
            new MovieMapper(new GenreMapper()),
            new CinemaMapper(new AuditoriumMapper(new SeatMapper())),
            new AuditoriumMapper(new SeatMapper())
    );

    @InjectMocks
    private ShowtimeServiceImpl showtimeService;

    private Movie sampleMovie;
    private Cinema sampleCinema;
    private Auditorium sampleAuditorium;
    private Showtime sampleShowtime;

    @BeforeEach
    void setUp() {
        sampleMovie = new Movie();
        sampleMovie.setId("mov-1");
        sampleMovie.setTitle("Inception");
        sampleMovie.setStatus(MovieStatus.NOW_SHOWING);
        sampleMovie.setDurationMinutes((short) 148);

        sampleCinema = new Cinema();
        sampleCinema.setId("cin-1");
        sampleCinema.setName("CineBook Landmark");
        sampleCinema.setStatus(CinemaStatus.ACTIVE);
        sampleCinema.setOpeningTime(LocalTime.of(8, 0));
        sampleCinema.setClosingTime(LocalTime.of(23, 0));
        sampleCinema.setAuditoriums(new HashSet<>());

        sampleAuditorium = new Auditorium();
        sampleAuditorium.setId("aud-1");
        sampleAuditorium.setName("Hall 1");
        sampleAuditorium.setType("IMAX");
        sampleAuditorium.setStatus(AuditoriumStatus.ACTIVE);
        sampleAuditorium.setTurnaroundMinutes((short) 15);
        sampleAuditorium.setSnapIntervalMinutes((short) 5);
        sampleAuditorium.setCinema(sampleCinema);
        sampleAuditorium.setSeats(new HashSet<>());

        sampleShowtime = new Showtime();
        sampleShowtime.setId("st-1");
        sampleShowtime.setMovie(sampleMovie);
        sampleShowtime.setAuditorium(sampleAuditorium);
        sampleShowtime.setFormat(ShowtimeFormat.IMAX);
        sampleShowtime.setLanguage("English");
        sampleShowtime.setSubtitle("Vietnamese");
        sampleShowtime.setStartTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        sampleShowtime.setEndTime(LocalDateTime.of(2026, 9, 1, 12, 28));
        sampleShowtime.setBasePrice(new BigDecimal("120000.00"));
        sampleShowtime.setStatus(ShowtimeStatus.SCHEDULED);
        sampleShowtime.setCreatedAt(LocalDateTime.now());
        sampleShowtime.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void createShowtime_DerivesEndTimeFromMovieDuration_IgnoresSuppliedEndTime() {
        // Request has wrong endTime (19:00:00) vs 148m duration (should be 16:28:00)
        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .subtitle("Vietnamese")
                .startTime(LocalDateTime.of(2026, 9, 1, 14, 0))
                .endTime(LocalDateTime.of(2026, 9, 1, 19, 0))
                .basePrice(new BigDecimal("120000.00"))
                .status(ShowtimeStatus.SCHEDULED)
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(inv -> {
            Showtime s = inv.getArgument(0);
            s.setId("st-new");
            return s;
        });

        ShowtimeDetailResponse response = showtimeService.createShowtime(request);

        assertNotNull(response);
        assertEquals("st-new", response.getId());
        assertEquals("Inception", response.getMovie().getTitle());
        assertEquals(LocalDateTime.of(2026, 9, 1, 14, 0), response.getStartTime());
        // End time is derived from duration (14:00 + 148 min = 16:28)
        assertEquals(LocalDateTime.of(2026, 9, 1, 16, 28), response.getEndTime());
        verify(showtimeRepository).save(argThat(s -> s.getEndTime().equals(LocalDateTime.of(2026, 9, 1, 16, 28))));
    }

    @Test
    void createShowtime_Reject_MovieEnded() {
        sampleMovie.setStatus(MovieStatus.ENDED);
        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 14, 0))
                .basePrice(new BigDecimal("120000.00"))
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> showtimeService.createShowtime(request));
        assertTrue(ex.getMessage().contains("Không thể tạo lịch chiếu cho phim đã kết thúc hoặc đang ẩn!"));
        verify(showtimeRepository, never()).save(any());
    }

    @Test
    void createShowtime_Reject_MovieHidden() {
        sampleMovie.setStatus(MovieStatus.HIDDEN);
        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 14, 0))
                .basePrice(new BigDecimal("120000.00"))
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));

        assertThrows(BadRequestException.class, () -> showtimeService.createShowtime(request));
    }

    @Test
    void createShowtime_Reject_AuditoriumMaintenance() {
        sampleAuditorium.setStatus(AuditoriumStatus.MAINTENANCE);
        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 14, 0))
                .basePrice(new BigDecimal("120000.00"))
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));

        ConflictException ex = assertThrows(ConflictException.class, () -> showtimeService.createShowtime(request));
        assertTrue(ex.getMessage().contains("Phòng chiếu đang bảo trì hoặc không khả dụng!"));
    }

    @Test
    void createShowtime_Reject_AuditoriumDecommissioned() {
        sampleAuditorium.setStatus(AuditoriumStatus.DECOMMISSIONED);
        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 14, 0))
                .basePrice(new BigDecimal("120000.00"))
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));

        ConflictException ex = assertThrows(ConflictException.class, () -> showtimeService.createShowtime(request));
        assertTrue(ex.getMessage().contains("DECOMMISSIONED"));
    }

    @Test
    void createShowtime_Reject_CinemaClosed() {
        sampleCinema.setStatus(CinemaStatus.CLOSED);
        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 14, 0))
                .basePrice(new BigDecimal("120000.00"))
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));

        assertThrows(BadRequestException.class, () -> showtimeService.createShowtime(request));
    }

    @Test
    void createShowtime_Reject_NegativePrice() {
        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 14, 0))
                .basePrice(new BigDecimal("-10.00"))
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> showtimeService.createShowtime(request));
        assertTrue(ex.getMessage().contains("Giá vé cơ bản không được âm!"));
    }

    @Test
    void createShowtime_Reject_Overlap() {
        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 10, 30))
                .basePrice(new BigDecimal("120000.00"))
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(List.of(sampleShowtime));

        ConflictException ex = assertThrows(ConflictException.class, () -> showtimeService.createShowtime(request));
        assertTrue(ex.getMessage().contains("Phòng chiếu đã có lịch chiếu trong khoảng thời gian này!"));
    }

    @Test
    void getPublicShowtimes_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Showtime> page = new PageImpl<>(List.of(sampleShowtime), pageable, 1);

        when(showtimeRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<ShowtimeSummaryResponse> result = showtimeService.getPublicShowtimes(
                "mov-1", "cin-1", "aud-1", LocalDate.of(2026, 9, 1), ShowtimeFormat.IMAX, "English", pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Inception", result.getContent().get(0).getMovieTitle());
    }

    @Test
    void getPublicShowtimeDetail_Success() {
        when(showtimeRepository.findById("st-1")).thenReturn(Optional.of(sampleShowtime));

        ShowtimeDetailResponse result = showtimeService.getPublicShowtimeDetail("st-1");

        assertNotNull(result);
        assertEquals("st-1", result.getId());
        assertEquals("Inception", result.getMovie().getTitle());
    }

    @Test
    void getPublicShowtimeDetail_AuditoriumMaintenance_ThrowsNotFound() {
        sampleAuditorium.setStatus(AuditoriumStatus.MAINTENANCE);
        when(showtimeRepository.findById("st-1")).thenReturn(Optional.of(sampleShowtime));

        assertThrows(ResourceNotFoundException.class, () -> showtimeService.getPublicShowtimeDetail("st-1"));
    }

    @Test
    void updateShowtime_Success_NoBookings() {
        UpdateShowtimeRequest request = UpdateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.THREE_D)
                .language("English")
                .subtitle("Vietnamese")
                .startTime(LocalDateTime.of(2026, 9, 1, 14, 0))
                .basePrice(new BigDecimal("150000.00"))
                .status(ShowtimeStatus.SCHEDULED)
                .build();

        when(showtimeRepository.findById("st-1")).thenReturn(Optional.of(sampleShowtime));
        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(bookingRepository.existsByShowtimeId("st-1")).thenReturn(false);
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(inv -> inv.getArgument(0));

        ShowtimeDetailResponse result = showtimeService.updateShowtime("st-1", request);

        assertNotNull(result);
        assertEquals(ShowtimeFormat.THREE_D, result.getFormat());
        assertEquals(new BigDecimal("150000.00"), result.getBasePrice());
        assertEquals(LocalDateTime.of(2026, 9, 1, 16, 28), result.getEndTime());
    }

    @Test
    void updateShowtime_RejectProtectedFields_WhenHasBookings() {
        UpdateShowtimeRequest request = UpdateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 12, 0)) // changed time
                .basePrice(new BigDecimal("120000.00"))
                .status(ShowtimeStatus.SCHEDULED)
                .build();

        when(showtimeRepository.findById("st-1")).thenReturn(Optional.of(sampleShowtime));
        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(bookingRepository.existsByShowtimeId("st-1")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> showtimeService.updateShowtime("st-1", request));
        assertTrue(ex.getMessage().contains("Không thể thay đổi phim, phòng chiếu hoặc thời gian của lịch chiếu đã có vé đặt!"));
        verify(showtimeRepository, never()).save(any());
    }

    @Test
    void deleteShowtime_PhysicalDelete_WhenNoBookings() {
        when(showtimeRepository.findById("st-1")).thenReturn(Optional.of(sampleShowtime));
        when(bookingRepository.existsByShowtimeId("st-1")).thenReturn(false);

        showtimeService.deleteShowtime("st-1");

        ((org.springframework.data.repository.CrudRepository<Showtime, String>) verify(showtimeRepository)).delete(sampleShowtime);
        verify(showtimeRepository, never()).save(any());
    }

    @Test
    void deleteShowtime_CancelStatus_WhenHasBookings() {
        when(showtimeRepository.findById("st-1")).thenReturn(Optional.of(sampleShowtime));
        when(bookingRepository.existsByShowtimeId("st-1")).thenReturn(true);

        showtimeService.deleteShowtime("st-1");

        assertEquals(ShowtimeStatus.CANCELLED, sampleShowtime.getStatus());
        verify(showtimeRepository).save(sampleShowtime);
        ((org.springframework.data.repository.CrudRepository<Showtime, String>) verify(showtimeRepository, never())).delete(any());
    }
}