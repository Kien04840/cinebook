package com.cinebook.task;

import com.cinebook.service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Tác vụ chạy ngầm định kỳ (Background Scheduled Task) quản lý vòng đời khuyến mãi:
 * 1. Tự động chuyển trạng thái sang EXPIRED đối với các mã ACTIVE hoặc PAUSED khi đã qua thời hạn (end_at < now).
 * 2. Tự động chuyển trạng thái sang PAUSED đối với các mã ACTIVE khi đã đạt giới hạn sử dụng (used_count >= usage_limit).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cinebook.promotion.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PromotionLifecycleTask {

    private final PromotionService promotionService;

    @Scheduled(
            fixedDelayString = "${cinebook.promotion.lifecycle.fixed-delay-ms:600000}",
            initialDelayString = "${cinebook.promotion.lifecycle.initial-delay-ms:15000}"
    )
    public void executePromotionLifecycleCheck() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int updated = promotionService.transitionPromotionStatuses(now);
            if (updated > 0) {
                log.info("PromotionLifecycleTask executed: {} promotions transitioned at {}", updated, now);
            }
        } catch (Exception ex) {
            log.error("PromotionLifecycleTask encountered an error: {}", ex.getMessage(), ex);
        }
    }
}

