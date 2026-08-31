package com.cinebook.task;

import com.cinebook.entity.Booking;
import com.cinebook.entity.BookingPromotion;
import com.cinebook.entity.Promotion;
import com.cinebook.enums.BookingStatus;
import com.cinebook.repository.BookingPromotionRepository;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.PromotionRepository;
import com.cinebook.repository.SeatHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCleanupTask {

    private final BookingRepository bookingRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final BookingPromotionRepository bookingPromotionRepository;
    private final PromotionRepository promotionRepository;

    @Scheduled(fixedDelay = 60000)
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredBookingsAndHolds() {
        LocalDateTime now = LocalDateTime.now();

        try {
            List<Booking> expiredBookings = bookingRepository.findExpiredBookings(BookingStatus.PENDING_PAYMENT, now);
            if (!expiredBookings.isEmpty()) {
                log.info("Found {} expired PENDING_PAYMENT bookings to process", expiredBookings.size());
                for (Booking booking : expiredBookings) {
                    booking.setBookingStatus(BookingStatus.EXPIRED);
                    seatHoldRepository.deleteByBookingId(booking.getId());

                    // Idempotent quota release for expired PENDING_PAYMENT booking
                    List<BookingPromotion> bookingPromotions = bookingPromotionRepository.findByBookingId(booking.getId());
                    for (BookingPromotion bp : bookingPromotions) {
                        Promotion promo = promotionRepository.findByIdWithLock(bp.getPromotion().getId()).orElse(null);
                        if (promo != null && promo.getUsedCount() > 0) {
                            promo.setUsedCount(promo.getUsedCount() - 1);
                            promotionRepository.save(promo);
                            log.info("Housekeeping released promotion quota for promo {}: new usedCount={}", promo.getCode(), promo.getUsedCount());
                        }
                    }
                }
                bookingRepository.saveAll(expiredBookings);
            }

            int deletedHolds = seatHoldRepository.deleteExpiredHolds(now);
            if (deletedHolds > 0 || !expiredBookings.isEmpty()) {
                log.info("Housekeeping completed: {} bookings expired, {} seat holds removed",
                        expiredBookings.size(), deletedHolds);
            }
        } catch (Exception ex) {
            log.error("Error occurred during booking and seat hold cleanup: {}", ex.getMessage(), ex);
        }
    }
}
