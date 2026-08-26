package com.cinebook.service;

import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.enums.MovieStatus;
import com.cinebook.enums.SchedulingConflictType;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.service.scheduling.SchedulingValidationResult;
import com.cinebook.service.scheduling.SchedulingValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingValidationServiceTest {

    private SchedulingValidationService validationService;
    private Movie sampleMovie;
    private Cinema sampleCinema;
    private Auditorium sampleAuditorium;

    @BeforeEach
    void setUp() {
        validationService = new SchedulingValidationService();

        sampleMovie = new Movie();
        sampleMovie.setId("mov-1");
        sampleMovie.setTitle("Inception");
        sampleMovie.setStatus(MovieStatus.NOW_SHOWING);
        sampleMovie.setDurationMinutes((short) 120);

        sampleCinema = new Cinema();
        sampleCinema.setId("cin-1");
        sampleCinema.setName("CineBook Landmark");
        sampleCinema.setStatus(CinemaStatus.ACTIVE);
        sampleCinema.setOpeningTime(LocalTime.of(8, 0));
        sampleCinema.setClosingTime(LocalTime.of(23, 0));

        sampleAuditorium = new Auditorium();
        sampleAuditorium.setId("aud-1");
        sampleAuditorium.setName("Hall 1");
        sampleAuditorium.setStatus(AuditoriumStatus.ACTIVE);
        sampleAuditorium.setTurnaroundMinutes((short) 15);
        sampleAuditorium.setSnapIntervalMinutes((short) 5);
        sampleAuditorium.setCinema(sampleCinema);
    }

    @Test
    void calculateEndTime_And_CalculateOccupancyEnd() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime end = validationService.calculateEndTime(start, 145);
        assertEquals(LocalDateTime.of(2026, 9, 1, 12, 25), end);

        LocalDateTime occEnd = validationService.calculateOccupancyEnd(start, 145, 15);
        assertEquals(LocalDateTime.of(2026, 9, 1, 12, 40), occEnd);
    }

    @Test
    void validateSlot_Success() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 12, 0);

        SchedulingValidationResult result = validationService.validateSlot(
                sampleMovie, sampleAuditorium, start, end, Collections.emptyList(), null, null, null);

        assertTrue(result.isValid());
        assertTrue(result.getConflicts().isEmpty());
    }

    @Test
    void validateSlot_MovieEnded_Fails() {
        sampleMovie.setStatus(MovieStatus.ENDED);
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 12, 0);

        SchedulingValidationResult result = validationService.validateSlot(
                sampleMovie, sampleAuditorium, start, end, Collections.emptyList(), null, null, null);

        assertFalse(result.isValid());
        assertEquals(SchedulingConflictType.MOVIE_NOT_AVAILABLE, result.getConflicts().get(0).getType());
    }

    @Test
    void validateSlot_AuditoriumMaintenance_Fails() {
        sampleAuditorium.setStatus(AuditoriumStatus.MAINTENANCE);
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 12, 0);

        SchedulingValidationResult result = validationService.validateSlot(
                sampleMovie, sampleAuditorium, start, end, Collections.emptyList(), null, null, null);

        assertFalse(result.isValid());
        assertEquals(SchedulingConflictType.AUDITORIUM_MAINTENANCE, result.getConflicts().get(0).getType());
    }

    @Test
    void validateSlot_AuditoriumDecommissioned_Fails() {
        sampleAuditorium.setStatus(AuditoriumStatus.DECOMMISSIONED);
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 12, 0);

        SchedulingValidationResult result = validationService.validateSlot(
                sampleMovie, sampleAuditorium, start, end, Collections.emptyList(), null, null, null);

        assertFalse(result.isValid());
        assertEquals(SchedulingConflictType.AUDITORIUM_DECOMMISSIONED, result.getConflicts().get(0).getType());
    }

    @Test
    void validateSlot_OutsideOperatingHours_Fails() {
        // Starts before opening (07:30 < 08:00)
        LocalDateTime startEarly = LocalDateTime.of(2026, 9, 1, 7, 30);
        LocalDateTime endEarly = LocalDateTime.of(2026, 9, 1, 9, 30);

        SchedulingValidationResult earlyResult = validationService.validateSlot(
                sampleMovie, sampleAuditorium, startEarly, endEarly, Collections.emptyList(), null, null, null);

        assertFalse(earlyResult.isValid());
        assertEquals(SchedulingConflictType.OUTSIDE_OPERATING_HOURS, earlyResult.getConflicts().get(0).getType());

        // Ends after closing (23:30 > 23:00)
        LocalDateTime startLate = LocalDateTime.of(2026, 9, 1, 21, 30);
        LocalDateTime endLate = LocalDateTime.of(2026, 9, 1, 23, 30);

        SchedulingValidationResult lateResult = validationService.validateSlot(
                sampleMovie, sampleAuditorium, startLate, endLate, Collections.emptyList(), null, null, null);

        assertFalse(lateResult.isValid());
        assertEquals(SchedulingConflictType.OUTSIDE_OPERATING_HOURS, lateResult.getConflicts().get(0).getType());
    }

    @Test
    void validateSlot_DirectShowtimeOverlap_Fails() {
        Showtime existing = new Showtime();
        existing.setId("st-exist");
        existing.setStartTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        existing.setEndTime(LocalDateTime.of(2026, 9, 1, 12, 0));
        existing.setStatus(ShowtimeStatus.SCHEDULED);

        // Candidate 11:00 -> 13:00 (overlaps 10:00 -> 12:00)
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 11, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 13, 0);

        SchedulingValidationResult result = validationService.validateSlot(
                sampleMovie, sampleAuditorium, start, end, List.of(existing), null, null, null);

        assertFalse(result.isValid());
        assertEquals(SchedulingConflictType.SHOWTIME_OVERLAP, result.getConflicts().get(0).getType());
    }

    @Test
    void validateSlot_TurnaroundViolation_Fails() {
        Showtime existing = new Showtime();
        existing.setId("st-exist");
        existing.setStartTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        existing.setEndTime(LocalDateTime.of(2026, 9, 1, 12, 0)); // Turnaround 15m -> occupied until 12:15
        existing.setStatus(ShowtimeStatus.SCHEDULED);

        // Candidate starts at 12:05 (during 12:00 -> 12:15 turnaround)
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 12, 5);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 14, 5);

        SchedulingValidationResult result = validationService.validateSlot(
                sampleMovie, sampleAuditorium, start, end, List.of(existing), null, null, null);

        assertFalse(result.isValid());
        assertEquals(SchedulingConflictType.TURNAROUND_VIOLATION, result.getConflicts().get(0).getType());
    }

    @Test
    void validateSlot_AfterTurnaroundFinished_Success() {
        Showtime existing = new Showtime();
        existing.setId("st-exist");
        existing.setStartTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        existing.setEndTime(LocalDateTime.of(2026, 9, 1, 12, 0)); // Turnaround 15m -> occupied until 12:15
        existing.setStatus(ShowtimeStatus.SCHEDULED);

        // Candidate starts at 12:15 (exact turnaround finish)
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 12, 15);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 14, 15);

        SchedulingValidationResult result = validationService.validateSlot(
                sampleMovie, sampleAuditorium, start, end, List.of(existing), null, null, null);

        assertTrue(result.isValid());
    }

    @Test
    void snapTimeUp_Comprehensive() {
        // exact boundary: 10:00 with snap 15 -> 10:00
        LocalDateTime t00 = LocalDateTime.of(2026, 9, 1, 10, 0);
        assertEquals(t00, validationService.snapTimeUp(t00, 15));

        // 1 min before boundary: 10:14 with snap 15 -> 10:15
        LocalDateTime t14 = LocalDateTime.of(2026, 9, 1, 10, 14);
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 15), validationService.snapTimeUp(t14, 15));

        // 1 min after boundary: 10:16 with snap 15 -> 10:30
        LocalDateTime t16 = LocalDateTime.of(2026, 9, 1, 10, 16);
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 30), validationService.snapTimeUp(t16, 15));

        // snap = 5
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 5), validationService.snapTimeUp(LocalDateTime.of(2026, 9, 1, 10, 1), 5));
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 10), validationService.snapTimeUp(LocalDateTime.of(2026, 9, 1, 10, 6), 5));

        // snap = 10
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 10), validationService.snapTimeUp(LocalDateTime.of(2026, 9, 1, 10, 3), 10));
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 20), validationService.snapTimeUp(LocalDateTime.of(2026, 9, 1, 10, 11), 10));

        // snap <= 0 or 1
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 14), validationService.snapTimeUp(LocalDateTime.of(2026, 9, 1, 10, 14), 1));
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 14), validationService.snapTimeUp(LocalDateTime.of(2026, 9, 1, 10, 14), 0));
    }
}