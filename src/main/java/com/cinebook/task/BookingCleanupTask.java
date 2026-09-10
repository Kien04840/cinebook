package com.cinebook.task;

import com.cinebook.entity.Booking;
import com.cinebook.entity.BookingPromotion;
import com.cinebook.entity.Promotion;
import com.cinebook.enums.BookingStatus;
import com.cinebook.repository.BookingPromotionRepository;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.PromotionRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.service.BookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Tác vụ chạy ngầm định kỳ (Background Scheduled Task) chuyên trách quét và dọn dẹp các đơn đặt vé
 * và trạng thái giữ chỗ (Seat Hold) đã quá hạn thanh toán 5 phút.
 * 
 * Cơ chế hoạt động:
 * 1. Tần suất: Chạy mỗi 10 giây một lần (fixedDelay = 10,000 ms, initialDelay = 5,000 ms).
 * 2. Xử lý phân trang theo lô (Batch Processing): Mỗi lần xử lý tối đa 100 đơn (batchSize = 100)
 *    nhằm giải phóng tài nguyên CPU/RAM, tránh lock bảng CSDL trong thời gian dài.
 * 3. Ủy thác xử lý toàn vẹn: Từng đơn hàng hết hạn được ủy thác cho BookingService.expireBookingIfHoldExpired
 *    để thực thi các nghiệp vụ: Lưu vết Snapshot vé CANCELLED, xóa SeatHold giải phóng ghế,
 *    hoàn trả Quota mã giảm giá và hủy các phiên thanh toán PENDING.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cinebook.booking.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class BookingCleanupTask {

    private final BookingRepository bookingRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final BookingPromotionRepository bookingPromotionRepository;
    private final PromotionRepository promotionRepository;
    private final BookingService bookingService;

    @Value("${cinebook.booking.cleanup.batch-size:100}")
    private int batchSize = 100;

    @Autowired
    public BookingCleanupTask(
            BookingRepository bookingRepository,
            SeatHoldRepository seatHoldRepository,
            BookingPromotionRepository bookingPromotionRepository,
            PromotionRepository promotionRepository,
            BookingService bookingService
    ) {
        this.bookingRepository = bookingRepository;
        this.seatHoldRepository = seatHoldRepository;
        this.bookingPromotionRepository = bookingPromotionRepository;
        this.promotionRepository = promotionRepository;
        this.bookingService = bookingService;
    }

    public BookingCleanupTask(
            BookingRepository bookingRepository,
            SeatHoldRepository seatHoldRepository,
            BookingPromotionRepository bookingPromotionRepository,
            PromotionRepository promotionRepository
    ) {
        this(bookingRepository, seatHoldRepository, bookingPromotionRepository, promotionRepository, null);
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * Phương thức được Spring Scheduler kích hoạt định kỳ.
     */
    @Scheduled(
            fixedDelayString = "${cinebook.booking.cleanup.fixed-delay-ms:10000}",
            initialDelayString = "${cinebook.booking.cleanup.initial-delay-ms:5000}"
    )
    public void cleanupExpiredBookingsAndHolds() {
        LocalDateTime now = LocalDateTime.now();
        int totalProcessed = 0;
        int totalExpired = 0;
        int totalFailed = 0;

        try {
            boolean hasMore = true;
            while (hasMore) {
                List<Booking> batch = Collections.emptyList();
                Page<Booking> expiredPage = null;
                try {
                    expiredPage = bookingRepository.findExpiredBookings(
                            BookingStatus.PENDING_PAYMENT,
                            now,
                            PageRequest.of(0, batchSize)
                    );
                } catch (Exception ignored) {
                }

                if (expiredPage != null && !expiredPage.isEmpty()) {
                    batch = expiredPage.getContent();
                } else {
                    List<Booking> unpaged = bookingRepository.findExpiredBookings(BookingStatus.PENDING_PAYMENT, now);
                    if (unpaged != null && !unpaged.isEmpty()) {
                        batch = unpaged.stream().limit(batchSize).toList();
                    }
                }

                if (batch.isEmpty()) {
                    break;
                }

                log.info("Processing batch of {} expired PENDING_PAYMENT booking(s)", batch.size());
                int batchNewlyExpiredCount = 0;

                for (Booking booking : batch) {
                    totalProcessed++;
                    try {
                        if (bookingService != null) {
                            Booking result = bookingService.expireBookingIfHoldExpired(booking);
                            if (result != null && result.getBookingStatus() == BookingStatus.EXPIRED) {
                                totalExpired++;
                                batchNewlyExpiredCount++;
                            }
                        } else {
                            expireBookingDirectly(booking);
                            totalExpired++;
                            batchNewlyExpiredCount++;
                        }
                    } catch (Exception e) {
                        totalFailed++;
                        log.error("Failed to expire booking code={}, id={}: {}",
                                booking.getBookingCode(), booking.getId(), e.getMessage());
                    }
                }

                // Nếu kích thước lô nhỏ hơn batchSize hoặc không còn đơn nào chuyển trạng thái trong đợt này, kết thúc vòng lặp
                if (batch.size() < batchSize || batchNewlyExpiredCount == 0) {
                    hasMore = false;
                }
            }

            int deletedHolds = (bookingService != null)
                    ? bookingService.cleanupExpiredSeatHolds(now)
                    : seatHoldRepository.deleteExpiredHolds(now);

            if (totalProcessed > 0 || deletedHolds > 0) {
                log.info("Booking cleanup completed: processed={}, expired={}, failed={}, orphanedHoldsRemoved={}",
                        totalProcessed, totalExpired, totalFailed, deletedHolds);
            } else {
                log.debug("Booking cleanup completed: no expired bookings or orphaned holds found");
            }
        } catch (Exception ex) {
            log.error("Error occurred during booking and seat hold cleanup: {}", ex.getMessage(), ex);
        }
    }

    private void expireBookingDirectly(Booking booking) {
        booking.setBookingStatus(BookingStatus.EXPIRED);
        seatHoldRepository.deleteByBookingId(booking.getId());

        List<BookingPromotion> bookingPromotions = bookingPromotionRepository.findByBookingId(booking.getId());
        for (BookingPromotion bp : bookingPromotions) {
            Promotion promo = promotionRepository.findByIdWithLock(bp.getPromotion().getId()).orElse(null);
            if (promo != null && promo.getUsedCount() > 0) {
                promo.setUsedCount(promo.getUsedCount() - 1);
                promotionRepository.save(promo);
                log.info("Direct cleanup released promotion quota for promo {}: new usedCount={}", promo.getCode(), promo.getUsedCount());
            }
        }
        bookingRepository.save(booking);
        bookingRepository.saveAll(List.of(booking));
    }
}
