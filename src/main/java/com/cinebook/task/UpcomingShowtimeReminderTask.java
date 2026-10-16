package com.cinebook.task;

import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.NotificationType;
import com.cinebook.repository.BookingRepository;
import com.cinebook.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tác vụ chạy ngầm định kỳ (Background Scheduled Task) quét các suất chiếu sắp diễn ra
 * trong vòng 24 giờ tới của các đơn đặt vé đã thanh toán (PAID) và gửi thông báo nhắc nhở
 * (In-App Notification) đến người dùng.
 *
 * Tính Idempotent: NotificationService kiểm tra existsByBookingIdAndType để không gửi trùng lặp.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cinebook.upcoming-showtime.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class UpcomingShowtimeReminderTask {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    @Scheduled(
            fixedDelayString = "${cinebook.upcoming-showtime.fixed-delay-ms:1800000}",
            initialDelayString = "${cinebook.upcoming-showtime.initial-delay-ms:30000}"
    )
    public void remindUpcomingShowtimes() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowEnd = now.plusHours(24);

            List<Booking> upcomingBookings = bookingRepository.findUpcomingPaidBookings(now, windowEnd, PageRequest.of(0, 100));
            int sentCount = 0;

            for (Booking booking : upcomingBookings) {
                if (booking.getUser() == null || booking.getShowtime() == null) {
                    continue;
                }
                Showtime showtime = booking.getShowtime();
                Movie movie = showtime.getMovie();
                Auditorium auditorium = showtime.getAuditorium();
                Cinema cinema = auditorium != null ? auditorium.getCinema() : null;

                String movieTitle = movie != null ? movie.getTitle() : "Phim";
                String startTimeStr = showtime.getStartTime() != null ? showtime.getStartTime().format(TIME_FORMATTER) : "";
                String cinemaName = cinema != null ? cinema.getName() : "Rạp chiếu";
                String auditoriumName = auditorium != null ? auditorium.getName() : "Phòng chiếu";

                String title = "Nhắc nhở: Suất chiếu sắp diễn ra!";
                String message = String.format("Suất chiếu phim \"%s\" sẽ bắt đầu lúc %s tại %s (%s). Vui lòng có mặt trước giờ chiếu 15 phút để hoàn tất soát vé.",
                        movieTitle, startTimeStr, cinemaName, auditoriumName);

                notificationService.createNotification(
                        booking.getUser(),
                        booking,
                        NotificationType.UPCOMING_SHOWTIME,
                        title,
                        message
                );
                sentCount++;
            }

            if (sentCount > 0) {
                log.info("UpcomingShowtimeReminderTask: Scanned and processed {} reminder notifications", sentCount);
            }
        } catch (Exception ex) {
            log.error("UpcomingShowtimeReminderTask encountered an error: {}", ex.getMessage(), ex);
        }
    }
}

