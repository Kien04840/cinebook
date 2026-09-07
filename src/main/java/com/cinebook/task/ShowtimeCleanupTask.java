package com.cinebook.task;

import com.cinebook.service.ShowtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "cinebook.showtime.cleanup.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ShowtimeCleanupTask {

    private final ShowtimeService showtimeService;

    @Scheduled(
            fixedDelayString = "${cinebook.showtime.cleanup.fixed-delay-ms:300000}",
            initialDelayString = "${cinebook.showtime.cleanup.initial-delay-ms:5000}"
    )
    public void cleanupFinishedShowtimes() {
        try {
            int updated = showtimeService.cleanupFinishedShowtimes();
            if (updated > 0) {
                log.info("Showtime cleanup task completed: {} showtimes transitioned to FINISHED", updated);
            }
        } catch (Exception ex) {
            log.error("Showtime cleanup task encountered an error: {}", ex.getMessage(), ex);
        }
    }
}

