package com.cinebook.repository;

import com.cinebook.entity.Booking;
import com.cinebook.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, String> {

    Optional<Booking> findByBookingCode(String bookingCode);

    boolean existsByBookingCode(String bookingCode);

    Page<Booking> findByUserId(
            String userId,
            Pageable pageable
    );

    Page<Booking> findByUserIdAndBookingStatus(
            String userId,
            BookingStatus bookingStatus,
            Pageable pageable
    );

    Page<Booking> findByShowtimeId(
            String showtimeId,
            Pageable pageable
    );

    Page<Booking> findByBookingStatus(
            BookingStatus bookingStatus,
            Pageable pageable
    );
}