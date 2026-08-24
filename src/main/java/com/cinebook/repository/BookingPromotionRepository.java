package com.cinebook.repository;

import com.cinebook.entity.BookingPromotion;
import com.cinebook.entity.BookingPromotionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingPromotionRepository
        extends JpaRepository<BookingPromotion, BookingPromotionId> {

    List<BookingPromotion> findByBookingId(String bookingId);

    List<BookingPromotion> findByPromotionId(String promotionId);
}