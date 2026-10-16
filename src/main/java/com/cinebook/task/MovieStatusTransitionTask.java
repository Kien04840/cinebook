package com.cinebook.task;

import com.cinebook.service.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ chạy ngầm định kỳ (Background Scheduled Task) tự động chuyển trạng thái phim
 * từ COMING_SOON sang NOW_SHOWING khi ngày hiện tại (múi giờ Asia/Ho_Chi_Minh) đạt ngày phát hành (releaseDate).
 * 
 * Tác vụ đảm bảo tính Idempotent và tuân thủ nguyên tắc chỉ ủy thác xử lý cho MovieService.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cinebook.movie.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MovieStatusTransitionTask {

    private final MovieService movieService;

    @Scheduled(
            fixedDelayString = "${cinebook.movie.lifecycle.fixed-delay-ms:900000}",
            initialDelayString = "${cinebook.movie.lifecycle.initial-delay-ms:15000}"
    )
    public void transitionComingSoonMovies() {
        try {
            int updated = movieService.transitionComingSoonMovies();
            if (updated > 0) {
                log.info("Movie lifecycle task completed: {} movies transitioned from COMING_SOON to NOW_SHOWING", updated);
            }
        } catch (Exception ex) {
            log.error("Movie lifecycle transition task encountered an error: {}", ex.getMessage(), ex);
        }
    }
}

