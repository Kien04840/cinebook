package com.cinebook.repository;

import com.cinebook.entity.BookingPromotion;
import com.cinebook.entity.BookingPromotionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingPromotionRepository
        extends JpaRepository<BookingPromotion, BookingPromotionId> {

    List<BookingPromotion> findByBookingId(String bookingId);

    Optional<BookingPromotion> findFirstByBookingId(String bookingId);

    List<BookingPromotion> findByPromotionId(String promotionId);

    void deleteByBookingId(String bookingId);
}