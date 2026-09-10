package com.cinebook.repository;

import com.cinebook.entity.BookingFood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingFoodRepository extends JpaRepository<BookingFood, String> {

    List<BookingFood> findByBookingId(String bookingId);

    List<BookingFood> findByBookingIdIn(List<String> bookingIds);
}

