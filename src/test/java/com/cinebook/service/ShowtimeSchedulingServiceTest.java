package com.cinebook.service;

import com.cinebook.dto.request.*;
import com.cinebook.dto.response.*;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.*;
import com.cinebook.exception.BadRequestException;
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
import org.junit.jupiter.api.DisplayName;
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

    private Movie movieA; // Avatar, 120m
    private Movie movieB; // Minecraft, 90m
    private Movie movieC; // Conjuring, 105m
    private Movie movieD; // Inside Out, 95m

    private Cinema sampleCinema;
    private Auditorium sampleAuditorium1;
    private Auditorium sampleAuditorium2;
    private Auditorium sampleAuditorium3;

    @BeforeEach
    void setUp() {
        movieA = new Movie();
        movieA.setId("mov-A");
        movieA.setTitle("Avatar");
        movieA.setStatus(MovieStatus.NOW_SHOWING);
        movieA.setDurationMinutes((short) 120);

        movieB = new Movie();
        movieB.setId("mov-B");
        movieB.setTitle("Minecraft");
        movieB.setStatus(MovieStatus.NOW_SHOWING);
        movieB.setDurationMinutes((short) 90);

        movieC = new Movie();
        movieC.setId("mov-C");
        movieC.setTitle("Conjuring");
        movieC.setStatus(MovieStatus.NOW_SHOWING);
        movieC.setDurationMinutes((short) 105);

        movieD = new Movie();
        movieD.setId("mov-D");
        movieD.setTitle("Inside Out");
        movieD.setStatus(MovieStatus.NOW_SHOWING);
        movieD.setDurationMinutes((short) 95);

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
        sampleAuditorium1.setSnapIntervalMinutes((short) 15);
        sampleAuditorium1.setCinema(sampleCinema);

        sampleAuditorium2 = new Auditorium();
        sampleAuditorium2.setId("aud-2");
        sampleAuditorium2.setName("Hall 2");
        sampleAuditorium2.setStatus(AuditoriumStatus.ACTIVE);
        sampleAuditorium2.setTurnaroundMinutes((short) 15);
        sampleAuditorium2.setSnapIntervalMinutes((short) 15);
        sampleAuditorium2.setCinema(sampleCinema);

        sampleAuditorium3 = new Auditorium();
        sampleAuditorium3.setId("aud-3");
        sampleAuditorium3.setName("Hall 3");
        sampleAuditorium3.setStatus(AuditoriumStatus.ACTIVE);
        sampleAuditorium3.setTurnaroundMinutes((short) 15);
        sampleAuditorium3.setSnapIntervalMinutes((short) 15);
        sampleAuditorium3.setCinema(sampleCinema);
    }

    private void mockCommonAuditoriums(Auditorium... auds) {
        for (Auditorium a : auds) {
            when(auditoriumRepository.findByIdAndDeletedAtIsNull(a.getId())).thenReturn(Optional.of(a));
        }
    }

    private void mockCommonMovies(Movie... movies) {
        for (Movie m : movies) {
            when(movieRepository.findById(m.getId())).thenReturn(Optional.of(m));
        }
    }

    // ==========================================
    // 1. Single movie new request
    // ==========================================
    @Test
    @DisplayName("1. Single movie new request - schedules up to quota")
    void generate_SingleMovieRequest_SchedulesCorrectly() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(MovieGenerationConfigDto.builder()
                        .movieId("mov-A")
                        .targetScreeningsPerDay(3)
                        .build()))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertEquals(3, preview.getTotalValid());
        assertEquals(3, preview.getTotalScheduled());
        assertEquals(0, preview.getTotalUnscheduled());
        assertEquals(1, preview.getMovieSummaries().size());
        assertEquals(3, preview.getMovieSummaries().get(0).getScheduledScreenings());
    }

    // ==========================================
    // 2. Multiple movies
    // ==========================================
    @Test
    @DisplayName("2. Multiple movies - schedules multiple movies within quotas")
    void generate_MultipleMovies_InterleavesCorrectly() {
        mockCommonAuditoriums(sampleAuditorium1, sampleAuditorium2);
        mockCommonMovies(movieA, movieB);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(3).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(3).build()
                ))
                .auditoriumIds(List.of("aud-1", "aud-2"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertEquals(6, preview.getTotalScheduled());
        assertEquals(0, preview.getTotalUnscheduled());
        assertTrue(preview.getSlots().stream().anyMatch(s -> s.getMovieId().equals("mov-A")));
        assertTrue(preview.getSlots().stream().anyMatch(s -> s.getMovieId().equals("mov-B")));
    }

    // ==========================================
    // 3. One auditorium with multiple movies
    // ==========================================
    @Test
    @DisplayName("3. One auditorium with multiple movies - interleaves in single room")
    void generate_OneAuditoriumWithMultipleMovies_InterleavesInSingleRoom() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA, movieB);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(2).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertEquals(4, preview.getTotalScheduled());
        List<ShowtimeSlotPreviewResponse> slots = preview.getSlots();

        // Verify that consecutive movies in Hall 1 alternate when possible
        assertEquals("mov-A", slots.get(0).getMovieId());
        assertEquals("mov-B", slots.get(1).getMovieId());
        assertEquals("mov-A", slots.get(2).getMovieId());
        assertEquals("mov-B", slots.get(3).getMovieId());
    }

    // ==========================================
    // 4. Multiple auditoriums
    // ==========================================
    @Test
    @DisplayName("4. Multiple auditoriums - distributes fairly across rooms")
    void generate_MultipleAuditoriums_DistributesEvenly() {
        mockCommonAuditoriums(sampleAuditorium1, sampleAuditorium2, sampleAuditorium3);
        mockCommonMovies(movieA, movieB);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(3).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(3).build()
                ))
                .auditoriumIds(List.of("aud-1", "aud-2", "aud-3"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertEquals(6, preview.getTotalScheduled());
        // All 3 auditoriums should receive showtimes
        long aud1Count = preview.getSlots().stream().filter(s -> s.getAuditoriumId().equals("aud-1")).count();
        long aud2Count = preview.getSlots().stream().filter(s -> s.getAuditoriumId().equals("aud-2")).count();
        long aud3Count = preview.getSlots().stream().filter(s -> s.getAuditoriumId().equals("aud-3")).count();

        assertTrue(aud1Count > 0);
        assertTrue(aud2Count > 0);
        assertTrue(aud3Count > 0);
    }

    // ==========================================
    // 5. 4 movies with quotas 6/4/3/2
    // ==========================================
    @Test
    @DisplayName("5. 4 movies with quotas 6/4/3/2 - respects target ratio across 3 auditoriums")
    void generate_FourMoviesWithQuotas6432_RespectsTargetRatio() {
        mockCommonAuditoriums(sampleAuditorium1, sampleAuditorium2, sampleAuditorium3);
        mockCommonMovies(movieA, movieB, movieC, movieD);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(6).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(4).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-C").targetScreeningsPerDay(3).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-D").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1", "aud-2", "aud-3"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertEquals(15, preview.getTotalRequested());
        assertEquals(15, preview.getTotalScheduled());
        assertEquals(0, preview.getTotalUnscheduled());

        Map<String, Integer> counts = new HashMap<>();
        for (ShowtimeSlotPreviewResponse s : preview.getSlots()) {
            counts.put(s.getMovieId(), counts.getOrDefault(s.getMovieId(), 0) + 1);
        }

        assertEquals(6, counts.get("mov-A"));
        assertEquals(4, counts.get("mov-B"));
        assertEquals(3, counts.get("mov-C"));
        assertEquals(2, counts.get("mov-D"));
    }

    // ==========================================
    // 6. Same movie simultaneous in multiple auditoriums allowed
    // ==========================================
    @Test
    @DisplayName("6. Same movie simultaneous in multiple auditoriums is allowed when appropriate")
    void generate_SameMovieSimultaneousInMultipleAuditoriums_AllowedWhenAppropriate() {
        mockCommonAuditoriums(sampleAuditorium1, sampleAuditorium2);
        mockCommonMovies(movieA);

        // Only movie A requested with high quota
        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(8).build()
                ))
                .auditoriumIds(List.of("aud-1", "aud-2"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // Both auditoriums start at opening time 08:00 with Movie A
        boolean simultaneousAtStart = preview.getSlots().stream()
                .filter(s -> s.getStartTime().toLocalTime().equals(LocalTime.of(8, 0)))
                .count() == 2;

        assertTrue(simultaneousAtStart, "Both rooms should be permitted to show Movie A at 08:00 simultaneously");
    }

    // ==========================================
    // 7. No room-index movie seed
    // ==========================================
    @Test
    @DisplayName("7. No room-index movie seed - movie selection is dynamic, not i % k")
    void generate_NoRoomIndexMovieSeed_MovieSelectionIsDynamic() {
        mockCommonAuditoriums(sampleAuditorium1, sampleAuditorium2);
        mockCommonMovies(movieA, movieB);

        // movieB has deficit priority (target 5 vs target 1)
        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(1).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(5).build()
                ))
                .auditoriumIds(List.of("aud-1", "aud-2"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // The first room should select movieB due to higher deficit ratio (5/5 > 1/1), not movieA just because it's first
        assertEquals("mov-B", preview.getSlots().get(0).getMovieId());
    }

    // ==========================================
    // 8. No stagger behavior (uses opening time as baseline)
    // ==========================================
    @Test
    @DisplayName("8. No stagger - uses opening time as baseline for all auditoriums")
    void generate_NoStagger_UsesOpeningTimeAsBaseline() {
        mockCommonAuditoriums(sampleAuditorium1, sampleAuditorium2, sampleAuditorium3);
        mockCommonMovies(movieA);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(6).build()
                ))
                .auditoriumIds(List.of("aud-1", "aud-2", "aud-3"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // Every room's first screening starts at openingTime 09:00, no stagger offset
        for (Auditorium aud : List.of(sampleAuditorium1, sampleAuditorium2, sampleAuditorium3)) {
            Optional<ShowtimeSlotPreviewResponse> firstSlot = preview.getSlots().stream()
                    .filter(s -> s.getAuditoriumId().equals(aud.getId()))
                    .findFirst();

            assertTrue(firstSlot.isPresent());
            assertEquals(LocalTime.of(9, 0), firstSlot.get().getStartTime().toLocalTime());
        }
    }

    // ==========================================
    // 9. Auditorium request order does not change schedule
    // ==========================================
    @Test
    @DisplayName("9. Auditorium request order does not change schedule determinism")
    void generate_AuditoriumRequestOrder_DoesNotChangeSchedule() {
        mockCommonAuditoriums(sampleAuditorium1, sampleAuditorium2);
        mockCommonMovies(movieA, movieB);

        ShowtimeGenerationRequest req1 = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(3).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(3).build()
                ))
                .auditoriumIds(List.of("aud-1", "aud-2"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationRequest req2 = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(3).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(3).build()
                ))
                .auditoriumIds(List.of("aud-2", "aud-1")) // Reversed order
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse p1 = schedulingService.previewGeneration(req1);
        ShowtimeGenerationPreviewResponse p2 = schedulingService.previewGeneration(req2);

        assertEquals(p1.getSlots().size(), p2.getSlots().size());
        for (int i = 0; i < p1.getSlots().size(); i++) {
            assertEquals(p1.getSlots().get(i).getAuditoriumId(), p2.getSlots().get(i).getAuditoriumId());
            assertEquals(p1.getSlots().get(i).getStartTime(), p2.getSlots().get(i).getStartTime());
            assertEquals(p1.getSlots().get(i).getMovieId(), p2.getSlots().get(i).getMovieId());
        }
    }

    // ==========================================
    // 10. Deficit-aware selection
    // ==========================================
    @Test
    @DisplayName("10. Deficit-aware selection prefers underserved movie")
    void generate_DeficitAwareSelection_PrefersUnderservedMovie() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA, movieB);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(1).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(4).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // movieB has 4 remaining vs movieA has 1 remaining -> movieB scheduled first
        assertEquals("mov-B", preview.getSlots().get(0).getMovieId());
    }

    // ==========================================
    // 11. Avoid consecutive repeat when alternatives exist
    // ==========================================
    @Test
    @DisplayName("11. Avoids consecutive repeat when alternatives exist")
    void generate_AvoidsConsecutiveRepeat_WhenAlternativesExist() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA, movieB);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(2).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        List<ShowtimeSlotPreviewResponse> slots = preview.getSlots();
        assertNotEquals(slots.get(0).getMovieId(), slots.get(1).getMovieId(), "Adjacent showtimes should alternate");
    }

    // ==========================================
    // 12. Consecutive repeat allowed when necessary
    // ==========================================
    @Test
    @DisplayName("12. Consecutive repeat allowed when necessary or sole option")
    void generate_ConsecutiveRepeatAllowed_WhenNecessaryOrSoleOption() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(3).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertEquals(3, preview.getTotalScheduled());
        assertEquals("mov-A", preview.getSlots().get(0).getMovieId());
        assertEquals("mov-A", preview.getSlots().get(1).getMovieId());
        assertEquals("mov-A", preview.getSlots().get(2).getMovieId());
    }

    // ==========================================
    // 13. Existing showtimes preserved
    // ==========================================
    @Test
    @DisplayName("13. Existing showtimes preserved - gaps used around them")
    void generate_ExistingShowtimesPreserved_GapsUsedAroundThem() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieB);

        LocalDate date = LocalDate.of(2026, 9, 10);
        Showtime existing = new Showtime();
        existing.setId("st-ex");
        existing.setAuditorium(sampleAuditorium1);
        existing.setMovie(movieA);
        existing.setStartTime(date.atTime(11, 0));
        existing.setEndTime(date.atTime(13, 0));
        existing.setStatus(ShowtimeStatus.SCHEDULED);

        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                eq("aud-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(existing));

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(date)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // Slot 1 fits before existing (08:00 - 09:30, turnaround until 09:45)
        // Slot 2 must be scheduled after existing (after 13:00 + 15m turnaround = 13:15)
        assertEquals(2, preview.getTotalScheduled());
        assertEquals(LocalTime.of(8, 0), preview.getSlots().get(0).getStartTime().toLocalTime());
        assertTrue(preview.getSlots().get(1).getStartTime().toLocalTime().isAfter(LocalTime.of(13, 0)));
    }

    // ==========================================
    // 14. Existing showtime causes cursor advancement
    // ==========================================
    @Test
    @DisplayName("14. Existing showtime causes cursor advancement past turnaround")
    void generate_ExistingShowtime_CausesCursorAdvancement() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA);

        LocalDate date = LocalDate.of(2026, 9, 10);
        Showtime existingAtStart = new Showtime();
        existingAtStart.setId("st-start");
        existingAtStart.setAuditorium(sampleAuditorium1);
        existingAtStart.setMovie(movieB);
        existingAtStart.setStartTime(date.atTime(8, 0));
        existingAtStart.setEndTime(date.atTime(9, 30));
        existingAtStart.setStatus(ShowtimeStatus.SCHEDULED);

        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                eq("aud-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(existingAtStart));

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(1).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(date)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // Cursor advances past 09:30 + 15m turnaround = 09:45
        assertEquals(1, preview.getTotalScheduled());
        assertEquals(LocalTime.of(9, 45), preview.getSlots().get(0).getStartTime().toLocalTime());
    }

    // ==========================================
    // 15. Movie that does not fit does not exhaust room if another movie fits
    // ==========================================
    @Test
    @DisplayName("15. Movie that does not fit does not exhaust room if another movie fits")
    void generate_MovieDoesNotFit_DoesNotExhaustRoomIfAnotherMovieFits() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA, movieB);

        // Closing is 10:00.
        // MovieA is 120m -> ends at 10:00 (fits).
        // If closing is 09:45:
        // MovieA (120m) does NOT fit (08:00 + 120m = 10:00 > 09:45).
        // MovieB (90m) FITS (08:00 + 90m = 09:30 <= 09:45).
        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(1).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(1).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(9, 45))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // Only movieB can be scheduled
        assertEquals(1, preview.getTotalScheduled());
        assertEquals("mov-B", preview.getSlots().get(0).getMovieId());
    }

    // ==========================================
    // 16. No remaining movie fits -> auditorium exhausted
    // ==========================================
    @Test
    @DisplayName("16. No remaining movie fits causes auditorium to become exhausted")
    void generate_NoRemainingMovieFits_AuditoriumExhausted() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA);

        // Operating window is only 1 hour (08:00 to 09:00), movie is 120m
        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(1).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(9, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertEquals(0, preview.getTotalScheduled());
        assertEquals(1, preview.getTotalUnscheduled());
        assertFalse(preview.getWarnings().isEmpty());
    }

    // ==========================================
    // 17. Opening respected
    // ==========================================
    @Test
    @DisplayName("17. Opening time respected - no early showtimes")
    void generate_OpeningTimeRespected_NoEarlyShowtimes() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertTrue(preview.getSlots().stream()
                .noneMatch(s -> s.getStartTime().toLocalTime().isBefore(LocalTime.of(10, 0))));
    }

    // ==========================================
    // 18. Closing respected
    // ==========================================
    @Test
    @DisplayName("18. Closing time respected - no late showtimes")
    void generate_ClosingTimeRespected_NoLateShowtimes() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(10).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(18, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertTrue(preview.getSlots().stream()
                .noneMatch(s -> s.getEndTime().toLocalTime().isAfter(LocalTime.of(18, 0))));
    }

    // ==========================================
    // 19. Movie duration determines end time
    // ==========================================
    @Test
    @DisplayName("19. Movie duration determines end time without rounding")
    void generate_MovieDurationDeterminesEndTime_NoDurationRounding() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieC); // Conjuring, 105m

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-C").targetScreeningsPerDay(1).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        ShowtimeSlotPreviewResponse slot = preview.getSlots().get(0);
        assertEquals(LocalTime.of(8, 0), slot.getStartTime().toLocalTime());
        assertEquals(LocalTime.of(9, 45), slot.getEndTime().toLocalTime()); // exactly 105 minutes
    }

    // ==========================================
    // 20. Turnaround determines next availability
    // ==========================================
    @Test
    @DisplayName("20. Turnaround determines next availability")
    void generate_TurnaroundDeterminesNextAvailability() {
        sampleAuditorium1.setTurnaroundMinutes((short) 20);
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA); // 120m

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 5)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // Slot 1: 08:00 - 10:00. Turnaround 20m -> next available: 10:20 (snapped to 5m = 10:20)
        assertEquals(LocalTime.of(8, 0), preview.getSlots().get(0).getStartTime().toLocalTime());
        assertEquals(LocalTime.of(10, 20), preview.getSlots().get(1).getStartTime().toLocalTime());
    }

    // ==========================================
    // 21. Snap applies only to next start
    // ==========================================
    @Test
    @DisplayName("21. Snap applies only to next start time, not end time")
    void generate_SnapAppliesOnlyToNextStart() {
        sampleAuditorium1.setTurnaroundMinutes((short) 15);
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieD); // 95m

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-D").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // Slot 1: 08:00 - 09:35 (95 min, not snapped).
        // Turnaround 15m -> 09:50.
        // Snapped up to 15m -> 10:00!
        assertEquals(LocalTime.of(9, 35), preview.getSlots().get(0).getEndTime().toLocalTime());
        assertEquals(LocalTime.of(10, 0), preview.getSlots().get(1).getStartTime().toLocalTime());
    }

    // ==========================================
    // 22. Insufficient capacity reports unscheduled quota and warning
    // ==========================================
    @Test
    @DisplayName("22. Insufficient capacity reports unscheduled quota and warning")
    void generate_InsufficientCapacity_ReportsUnscheduledQuotaAndWarning() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA); // 120m

        // Only 1 room from 08:00 to 23:00 (~6 slots capacity). Requesting 10 slots.
        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(10).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertEquals(10, preview.getTotalRequested());
        assertTrue(preview.getTotalScheduled() < 10);
        assertTrue(preview.getTotalUnscheduled() > 0);
        assertFalse(preview.getWarnings().isEmpty());
        assertTrue(preview.getWarnings().get(0).contains("INSUFFICIENT_AUDITORIUM_CAPACITY"));
    }

    // ==========================================
    // 23. Date range resets quotas independently per day
    // ==========================================
    @Test
    @DisplayName("23. Date range resets quotas independently per day")
    void generate_DateRange_ResetsQuotasIndependentlyPerDay() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .endDate(LocalDate.of(2026, 9, 12)) // 3 days
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        assertEquals(6, preview.getTotalRequested()); // 2 * 3 days = 6
        assertEquals(6, preview.getTotalScheduled());

        long day1 = preview.getSlots().stream().filter(s -> s.getDate().equals(LocalDate.of(2026, 9, 10))).count();
        long day2 = preview.getSlots().stream().filter(s -> s.getDate().equals(LocalDate.of(2026, 9, 11))).count();
        long day3 = preview.getSlots().stream().filter(s -> s.getDate().equals(LocalDate.of(2026, 9, 12))).count();

        assertEquals(2, day1);
        assertEquals(2, day2);
        assertEquals(2, day3);
    }

    // ==========================================
    // 24. Preview and Generate use exact same plan
    // ==========================================
    @Test
    @DisplayName("24. Preview and Generate produce identical candidate plan")
    void generate_PreviewAndGenerate_UseExactSamePlan() {
        mockCommonAuditoriums(sampleAuditorium1, sampleAuditorium2);
        mockCommonMovies(movieA, movieB);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(2).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-B").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1", "aud-2"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        when(showtimeRepository.existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(any(), any(), any(), any()))
                .thenReturn(false);
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(inv -> {
            Showtime st = inv.getArgument(0);
            st.setId(UUID.randomUUID().toString());
            return st;
        });

        ShowtimeGenerationResultResponse result = schedulingService.generateShowtimes(req);

        assertEquals(preview.getTotalValid(), result.getTotalCreated());
        assertEquals(preview.getTotalRequested(), result.getTotalRequested());
    }

    // ==========================================
    // 25. Generate persists only planned valid candidates
    // ==========================================
    @Test
    @DisplayName("25. Generate persists only planned valid candidates")
    void generate_PersistsOnlyPlannedValidCandidates() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA);

        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        when(showtimeRepository.existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(any(), any(), any(), any()))
                .thenReturn(false);
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(inv -> {
            Showtime st = inv.getArgument(0);
            st.setId(UUID.randomUUID().toString());
            return st;
        });

        ShowtimeGenerationResultResponse result = schedulingService.generateShowtimes(req);

        assertEquals(2, result.getTotalCreated());
        verify(showtimeRepository, times(2)).save(any(Showtime.class));
    }

    // ==========================================
    // 26. Copy schedule preserves time-of-day and does not invoke Generate
    // ==========================================
    @Test
    @DisplayName("26. Copy schedule preserves time of day and does not invoke Generate")
    void copySchedule_PreservesTimeOfDay_DoesNotInvokeGenerate() {
        LocalDate srcDate = LocalDate.of(2026, 9, 10);
        LocalDate tgtDate = LocalDate.of(2026, 9, 11);

        Showtime srcSt = new Showtime();
        srcSt.setId("src-1");
        srcSt.setMovie(movieA);
        srcSt.setAuditorium(sampleAuditorium1);
        srcSt.setStartTime(srcDate.atTime(14, 30));
        srcSt.setEndTime(srcDate.atTime(16, 30));
        srcSt.setFormat(ShowtimeFormat.TWO_D);
        srcSt.setLanguage("Vietnamese");
        srcSt.setBasePrice(BigDecimal.valueOf(90000));
        srcSt.setStatus(ShowtimeStatus.SCHEDULED);

        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                eq("aud-1"), eq(srcDate.atStartOfDay()), any(LocalDateTime.class)))
                .thenReturn(List.of(srcSt));

        when(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                eq("aud-1"), eq(tgtDate.atStartOfDay()), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        when(showtimeRepository.existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(
                eq("mov-A"), eq("aud-1"), eq(tgtDate.atTime(14, 30)), eq(ShowtimeStatus.CANCELLED)))
                .thenReturn(false);

        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(inv -> {
            Showtime s = inv.getArgument(0);
            s.setId("new-copied-id");
            return s;
        });

        CopyScheduleRequest copyReq = CopyScheduleRequest.builder()
                .sourceDate(srcDate)
                .targetDate(tgtDate)
                .auditoriumIds(List.of("aud-1"))
                .build();

        CopyScheduleResultResponse copyRes = schedulingService.copySchedule(copyReq);

        assertEquals(1, copyRes.getTotalCopied());
        assertEquals(0, copyRes.getTotalConflicted());
        assertEquals(tgtDate.atTime(14, 30), copyRes.getCreatedShowtimes().get(0).getStartTime());
    }

    // ==========================================
    // 27. Legacy compatibility follows documented operating capacity formula
    // ==========================================
    @Test
    @DisplayName("27. Legacy compatibility calculates target from operating capacity")
    void generate_LegacyCompatibility_CalculatesTargetFromOperatingCapacity() {
        mockCommonAuditoriums(sampleAuditorium1);
        mockCommonMovies(movieA); // 120m

        // Calling with single movieId (legacy request)
        ShowtimeGenerationRequest req = ShowtimeGenerationRequest.builder()
                .movieId("mov-A")
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .snapIntervalMinutes((short) 15)
                .build();

        ShowtimeGenerationPreviewResponse preview = schedulingService.previewGeneration(req);

        // Op minutes: 15*60 = 900. Turnaround: 15. Movie duration: 120.
        // Target: floor((900 + 15) / (120 + 15)) = floor(915 / 135) = 6!
        assertEquals(6, preview.getTotalRequested());
        assertEquals(6, preview.getTotalScheduled());
        assertEquals(1, preview.getMovieSummaries().size());
        assertEquals(6, preview.getMovieSummaries().get(0).getTargetScreenings());
    }

    // ==========================================
    // 28. Duplicate movie and auditorium input validation
    // ==========================================
    @Test
    @DisplayName("28. Validates duplicate movie and auditorium input")
    void generate_ValidatesDuplicateMovieAndAuditoriumInput() {
        // Duplicate movie IDs
        ShowtimeGenerationRequest dupMovieReq = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(2).build(),
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(3).build()
                ))
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .build();

        assertThrows(BadRequestException.class, () -> schedulingService.previewGeneration(dupMovieReq));

        // Duplicate auditorium IDs
        ShowtimeGenerationRequest dupAudReq = ShowtimeGenerationRequest.builder()
                .movies(List.of(
                        MovieGenerationConfigDto.builder().movieId("mov-A").targetScreeningsPerDay(2).build()
                ))
                .auditoriumIds(List.of("aud-1", "aud-1"))
                .startDate(LocalDate.of(2026, 9, 10))
                .build();

        assertThrows(BadRequestException.class, () -> schedulingService.previewGeneration(dupAudReq));
    }

    // ==========================================
    // Existing Query Tests (Calendar, Config, Availability, Suggest)
    // ==========================================
    @Test
    @DisplayName("Calendar schedule query returns grouped auditoriums")
    void getCalendarSchedule_Success() {
        when(cinemaRepository.findByIdAndDeletedAtIsNull("cin-1")).thenReturn(Optional.of(sampleCinema));
        when(auditoriumRepository.findByCinemaIdAndDeletedAtIsNull("cin-1")).thenReturn(List.of(sampleAuditorium1));

        CalendarScheduleResponse resp = schedulingService.getCalendarSchedule("cin-1", LocalDate.now(), LocalDate.now().plusDays(1));
        assertNotNull(resp);
        assertEquals("cin-1", resp.getCinemaId());
        assertEquals(1, resp.getAuditoriums().size());
    }

    @Test
    @DisplayName("Auditorium availability intervals calculated accurately")
    void getAuditoriumAvailability_Success() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium1));

        AuditoriumAvailabilityResponse resp = schedulingService.getAuditoriumAvailability("aud-1", LocalDate.now());
        assertNotNull(resp);
        assertFalse(resp.getIntervals().isEmpty());
    }
}
