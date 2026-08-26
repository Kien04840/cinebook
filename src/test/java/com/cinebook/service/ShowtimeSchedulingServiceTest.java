package com.cinebook.service;

import com.cinebook.dto.request.*;
import com.cinebook.dto.response.*;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.enums.MovieStatus;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.mapper.AuditoriumMapper;
import com.cinebook.mapper.CinemaMapper;
import com.cinebook.mapper.GenreMapper;
import com.cinebook.mapper.MovieMapper;
import com.cinebook.mapper.SeatMapper;
import com.cinebook.mapper.ShowtimeMapper;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.CinemaRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.service.impl.ShowtimeSchedulingServiceImpl;
import com.cinebook.service.scheduling.SchedulingValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowtimeSchedulingServiceTest {

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private AuditoriumRepository auditoriumRepository;

    @Mock
    private CinemaRepository cinemaRepository;

    @Spy
    private SchedulingValidationService validationService = new SchedulingValidationService();

    @Spy
    private ShowtimeMapper showtimeMapper = new ShowtimeMapper(
            new MovieMapper(new GenreMapper()),
            new CinemaMapper(new AuditoriumMapper(new SeatMapper())),
            new AuditoriumMapper(new SeatMapper())
    );

    @InjectMocks
    private ShowtimeSchedulingServiceImpl schedulingService;

    private Movie sampleMovie;
    private Cinema sampleCinema;
    private Auditorium sampleAuditorium1;
    private Auditorium sampleAuditorium2;

    @BeforeEach
    void setUp() {
        sampleMovie = new Movie();
        sampleMovie.setId("mov-1");
        sampleMovie.setTitle("Inception");
        sampleMovie.setStatus(MovieStatus.NOW_SHOWING);
        sampleMovie.setDurationMinutes((short) 112);

        sampleCinema = new Cinema();
        sampleCinema.setId("cin-1");
        sampleCinema.setName("CineBook Landmark");
        sampleCinema.setStatus(CinemaStatus.ACTIVE);
        sampleCinema.setOpeningTime(LocalTime.of(8, 0));
        sampleCinema.setClosingTime(LocalTime.of(23, 0));

        sampleAuditorium1 = new Auditorium();
        sampleAuditorium1.setId("aud-1");
        sampleAuditorium1.setName("Hall 1");
        sampleAuditorium1.setStatus(AuditoriumStatus.ACTIVE);
        sampleAuditorium1.setTurnaroundMinutes((short) 15);
        sampleAuditorium1.setSnapIntervalMinutes((short) 5);
        sampleAuditorium1.setCinema(sampleCinema);
        sampleAuditorium1.setSeats(new HashSet<>());

        sampleAuditorium2 = new Auditorium();
        sampleAuditorium2.setId("aud-2");
        sampleAuditorium2.setName("Hall 2");
        sampleAuditorium2.setStatus(AuditoriumStatus.ACTIVE);
        sampleAuditorium2.setTurnaroundMinutes((short) 15);
        sampleAuditorium2.setSnapIntervalMinutes((short) 5);
        sampleAuditorium2.setCinema(sampleCinema);
        sampleAuditorium2.setSeats(new HashSet<>());
    }

    @Test
    void previewGeneration_CalculatesSlotsWithoutPersisting() {
        ShowtimeGenerationRequest request = ShowtimeGenerationRequest.builder()
                .movieId("mov-1")
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 1))
                .snapIntervalMinutes((short) 5)
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium1));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(Collections.emptyList());

        ShowtimeGenerationPreviewResponse response = schedulingService.previewGeneration(request);

        assertNotNull(response);
        assertTrue(response.getTotalProposed() > 0);
        assertEquals(response.getTotalProposed(), response.getTotalValid());
        assertEquals(0, response.getTotalConflicted());

        // 1st slot: 08:00 -> 09:52
        ShowtimeSlotPreviewResponse firstSlot = response.getSlots().get(0);
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0), firstSlot.getStartTime());
        assertEquals(LocalDateTime.of(2026, 9, 1, 9, 52), firstSlot.getEndTime());

        // 2nd slot: 09:52 + 15m turnaround = 10:07 -> snap to 5m = 10:10 -> 12:02
        ShowtimeSlotPreviewResponse secondSlot = response.getSlots().get(1);
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 10), secondSlot.getStartTime());
        assertEquals(LocalDateTime.of(2026, 9, 1, 12, 2), secondSlot.getEndTime());

        // No database writes
        verify(showtimeRepository, never()).save(any());
    }

    @Test
    void generateShowtimes_PersistsValidSlotsAndSkipsDuplicates() {
        ShowtimeGenerationRequest request = ShowtimeGenerationRequest.builder()
                .movieId("mov-1")
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 1))
                .snapIntervalMinutes((short) 5)
                .format(ShowtimeFormat.TWO_D)
                .basePrice(new BigDecimal("100000.00"))
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium1));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(showtimeRepository.existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(any(), any(), any(), any()))
                .thenReturn(false);
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(inv -> {
            Showtime s = inv.getArgument(0);
            s.setId(UUID.randomUUID().toString());
            return s;
        });

        ShowtimeGenerationResultResponse response = schedulingService.generateShowtimes(request);

        assertNotNull(response);
        assertTrue(response.getTotalCreated() > 0);
        assertEquals(0, response.getTotalSkipped());
        assertEquals(0, response.getTotalConflicted());
        verify(showtimeRepository, atLeastOnce()).save(any(Showtime.class));
    }

    @Test
    void generateShowtimes_Idempotency_SkipsExistingSlots() {
        ShowtimeGenerationRequest request = ShowtimeGenerationRequest.builder()
                .movieId("mov-1")
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 1))
                .snapIntervalMinutes((short) 5)
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium1));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        // Simulate that every slot already exists
        when(showtimeRepository.existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(any(), any(), any(), any()))
                .thenReturn(true);

        ShowtimeGenerationResultResponse response = schedulingService.generateShowtimes(request);

        assertNotNull(response);
        assertEquals(0, response.getTotalCreated());
        assertTrue(response.getTotalSkipped() > 0);
        verify(showtimeRepository, never()).save(any());
    }

    @Test
    void generateShowtimes_StaggeredStart_OffsetsRooms() {
        ShowtimeGenerationRequest request = ShowtimeGenerationRequest.builder()
                .movieId("mov-1")
                .auditoriumIds(List.of("aud-1", "aud-2"))
                .startDate(LocalDate.of(2026, 9, 1))
                .staggerIntervalMinutes((short) 5)
                .snapIntervalMinutes((short) 5)
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium1));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-2")).thenReturn(Optional.of(sampleAuditorium2));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(request);

        // Room 1 start is 08:00
        ShowtimeSlotPreviewResponse room1First = preview.getSlots().stream()
                .filter(s -> s.getAuditoriumId().equals("aud-1"))
                .findFirst().orElseThrow();
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0), room1First.getStartTime());

        // Room 2 start is 08:00 + 5m stagger = 08:05
        ShowtimeSlotPreviewResponse room2First = preview.getSlots().stream()
                .filter(s -> s.getAuditoriumId().equals("aud-2"))
                .findFirst().orElseThrow();
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 5), room2First.getStartTime());
    }

    @Test
    void copySchedule_CreatesIndependentRecordsOnTargetDate() {
        Showtime sourceShowtime = new Showtime();
        sourceShowtime.setId("st-src-1");
        sourceShowtime.setMovie(sampleMovie);
        sourceShowtime.setAuditorium(sampleAuditorium1);
        sourceShowtime.setStartTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        sourceShowtime.setEndTime(LocalDateTime.of(2026, 9, 1, 11, 52));
        sourceShowtime.setBasePrice(new BigDecimal("120000.00"));
        sourceShowtime.setFormat(ShowtimeFormat.TWO_D);
        sourceShowtime.setLanguage("Vietnamese");
        sourceShowtime.setStatus(ShowtimeStatus.SCHEDULED);

        CopyScheduleRequest request = CopyScheduleRequest.builder()
                .sourceDate(LocalDate.of(2026, 9, 1))
                .targetDate(LocalDate.of(2026, 9, 2))
                .cinemaId("cin-1")
                .build();

        when(showtimeRepository.findCalendarShowtimes(eq("cin-1"), any(), any()))
                .thenReturn(List.of(sourceShowtime));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(showtimeRepository.existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(any(), any(), any(), any()))
                .thenReturn(false);
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(inv -> {
            Showtime s = inv.getArgument(0);
            s.setId("st-target-new");
            return s;
        });

        CopyScheduleResultResponse response = schedulingService.copySchedule(request);

        assertNotNull(response);
        assertEquals(1, response.getTotalCopied());
        assertEquals(0, response.getTotalSkipped());
        assertEquals(0, response.getTotalConflicted());

        verify(showtimeRepository).save(argThat(st ->
                st.getStartTime().equals(LocalDateTime.of(2026, 9, 2, 10, 0)) &&
                st.getEndTime().equals(LocalDateTime.of(2026, 9, 2, 11, 52))
        ));
    }

    @Test
    void getCalendarSchedule_GroupsByAuditorium() {
        Showtime st1 = new Showtime();
        st1.setId("st-1");
        st1.setMovie(sampleMovie);
        st1.setAuditorium(sampleAuditorium1);
        st1.setStartTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        st1.setEndTime(LocalDateTime.of(2026, 9, 1, 11, 52));
        st1.setStatus(ShowtimeStatus.SCHEDULED);

        when(cinemaRepository.findByIdAndDeletedAtIsNull("cin-1")).thenReturn(Optional.of(sampleCinema));
        when(auditoriumRepository.findByCinemaIdAndDeletedAtIsNull("cin-1")).thenReturn(List.of(sampleAuditorium1, sampleAuditorium2));
        when(showtimeRepository.findCalendarShowtimes(eq("cin-1"), any(), any())).thenReturn(List.of(st1));

        CalendarScheduleResponse response = schedulingService.getCalendarSchedule("cin-1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));

        assertNotNull(response);
        assertEquals("cin-1", response.getCinemaId());
        assertEquals(2, response.getAuditoriums().size());
        assertEquals("Hall 1", response.getAuditoriums().get(0).getAuditoriumName());
        assertEquals(1, response.getAuditoriums().get(0).getShowtimes().size());
        assertEquals("Hall 2", response.getAuditoriums().get(1).getAuditoriumName());
        assertEquals(0, response.getAuditoriums().get(1).getShowtimes().size());
    }

    @Test
    void validateSingleSlot_Success() {
        ValidateShowtimeSlotRequest request = ValidateShowtimeSlotRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .startTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium1));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(Collections.emptyList());

        ValidateShowtimeSlotResponse response = schedulingService.validateSingleSlot(request);

        assertNotNull(response);
        assertTrue(response.isValid());
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 0), response.getCalculatedStartTime());
        assertEquals(LocalDateTime.of(2026, 9, 1, 11, 52), response.getCalculatedEndTime());
        assertEquals((short) 112, response.getMovieDurationMinutes());
        assertEquals(LocalDateTime.of(2026, 9, 1, 12, 7), response.getOccupancyEndTime()); // 11:52 + 15m
    }

    @Test
    void suggestNextSlot_FindsAvailableSlot() {
        SuggestShowtimeSlotRequest request = SuggestShowtimeSlotRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .requestedStartTime(LocalDateTime.of(2026, 9, 1, 9, 2))
                .snapIntervalMinutes((short) 15)
                .build();

        when(movieRepository.findById("mov-1")).thenReturn(Optional.of(sampleMovie));
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium1));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(Collections.emptyList());

        SuggestShowtimeSlotResponse response = schedulingService.suggestNextSlot(request);

        assertNotNull(response);
        assertTrue(response.isAvailable());
        assertEquals(LocalDateTime.of(2026, 9, 1, 9, 15), response.getSuggestedStartTime()); // 09:02 snapped to 15m = 09:15
        assertEquals(LocalDateTime.of(2026, 9, 1, 11, 7), response.getSuggestedEndTime()); // 09:15 + 112m = 11:07
    }

    @Test
    void getCinemaSchedulingConfig_Success() {
        when(cinemaRepository.findByIdAndDeletedAtIsNull("cin-1")).thenReturn(Optional.of(sampleCinema));
        when(auditoriumRepository.findByCinemaIdAndDeletedAtIsNull("cin-1")).thenReturn(List.of(sampleAuditorium1));

        CinemaSchedulingConfigResponse response = schedulingService.getCinemaSchedulingConfig("cin-1");

        assertNotNull(response);
        assertEquals("cin-1", response.getCinemaId());
        assertEquals(LocalTime.of(8, 0), response.getOpeningTime());
        assertEquals(LocalTime.of(23, 0), response.getClosingTime());
        assertEquals(1, response.getAuditoriums().size());
        assertEquals("Hall 1", response.getAuditoriums().get(0).getName());
    }

    @Test
    void getAuditoriumAvailability_Success() {
        Showtime st1 = new Showtime();
        st1.setId("st-1");
        st1.setMovie(sampleMovie);
        st1.setAuditorium(sampleAuditorium1);
        st1.setStartTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        st1.setEndTime(LocalDateTime.of(2026, 9, 1, 11, 52));
        st1.setStatus(ShowtimeStatus.SCHEDULED);

        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium1));
        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(eq("aud-1"), any(), any()))
                .thenReturn(List.of(st1));

        AuditoriumAvailabilityResponse response = schedulingService.getAuditoriumAvailability("aud-1", LocalDate.of(2026, 9, 1));

        assertNotNull(response);
        assertEquals("aud-1", response.getAuditoriumId());
        assertEquals(4, response.getIntervals().size()); // AVAILABLE(08:00->10:00), SHOWTIME(10:00->11:52), TURNAROUND(11:52->12:07), AVAILABLE(12:07->23:00)
    }
}
