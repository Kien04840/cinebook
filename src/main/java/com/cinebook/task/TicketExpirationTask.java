package com.cinebook.task;

import com.cinebook.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Tác vụ chạy ngầm định kỳ (Background Scheduled Task) quét và tự động chuyển
 * trạng thái vé từ VALID sang EXPIRED khi suất chiếu đã kết thúc (showtime.end_time < now)
 * và đơn đặt vé đã thanh toán (PAID).
 *
 * Thực hiện batch update trực tiếp dưới tầng cơ sở dữ liệu để tối ưu hiệu năng bộ nhớ.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cinebook.ticket.expiration.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class TicketExpirationTask {

    private final BookingService bookingService;

    @Scheduled(
            fixedDelayString = "${cinebook.ticket.expiration.fixed-delay-ms:300000}",
            initialDelayString = "${cinebook.ticket.expiration.initial-delay-ms:10000}"
    )
    public void expirePastTickets() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int updated = bookingService.expirePastTickets(now);
            if (updated > 0) {
                log.info("TicketExpirationTask executed: {} past tickets marked as EXPIRED at {}", updated, now);
            }
        } catch (Exception ex) {
            log.error("TicketExpirationTask encountered an error: {}", ex.getMessage(), ex);
        }
    }
}

