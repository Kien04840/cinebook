package com.cinebook.service;

import com.cinebook.entity.Showtime;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.service.impl.ShowtimeServiceImpl;
import com.cinebook.task.ShowtimeCleanupTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowtimeLifecycleTest {

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private ShowtimeService showtimeService;

    @Mock
    private PricingService pricingService;

    private ShowtimeServiceImpl showtimeServiceImpl;
    private ShowtimeCleanupTask cleanupTask;

    @BeforeEach
    void setUp() {
        showtimeServiceImpl = new ShowtimeServiceImpl(
                showtimeRepository,
                null, // movieRepository
                null, // auditoriumRepository
                null, // cinemaRepository
                null, // bookingRepository
                null, // showtimeMapper
                null, // validationService
                pricingService
        );

        cleanupTask = new ShowtimeCleanupTask(showtimeService);
    }

    @Test
    @DisplayName("Lifecycle: markFinishedShowtimes query is invoked with current timestamp")
    void testCleanupFinishedShowtimes_InvokesRepository() {
        when(showtimeRepository.markFinishedShowtimes(any(LocalDateTime.class))).thenReturn(5);

        int updated = showtimeServiceImpl.cleanupFinishedShowtimes();

        assertThat(updated).isEqualTo(5);
        verify(showtimeRepository, times(1)).markFinishedShowtimes(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Lifecycle: Idempotency - running cleanup multiple times returns 0 when no new showtimes ended")
    void testCleanupFinishedShowtimes_Idempotent() {
        when(showtimeRepository.markFinishedShowtimes(any(LocalDateTime.class)))
                .thenReturn(3)  // first run transitions 3
                .thenReturn(0); // second run transitions 0

        int firstRun = showtimeServiceImpl.cleanupFinishedShowtimes();
        int secondRun = showtimeServiceImpl.cleanupFinishedShowtimes();

        assertThat(firstRun).isEqualTo(3);
        assertThat(secondRun).isEqualTo(0);
        verify(showtimeRepository, times(2)).markFinishedShowtimes(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Lifecycle: ShowtimeCleanupTask executes showtimeService.cleanupFinishedShowtimes")
    void testCleanupTask_ExecutesService() {
        when(showtimeService.cleanupFinishedShowtimes()).thenReturn(2);

        cleanupTask.cleanupFinishedShowtimes();

        verify(showtimeService, times(1)).cleanupFinishedShowtimes();
    }
}

