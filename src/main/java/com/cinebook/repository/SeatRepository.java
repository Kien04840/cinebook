package com.cinebook.repository;

import com.cinebook.entity.Seat;
import com.cinebook.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, String> {

    List<Seat> findByAuditoriumId(String auditoriumId);

    List<Seat> findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(String auditoriumId);

    List<Seat> findByAuditoriumIdAndStatus(String auditoriumId, SeatStatus status);

    Optional<Seat> findByAuditoriumIdAndRowLabelAndSeatNumber(
            String auditoriumId,
            String rowLabel,
            Short seatNumber
    );

    boolean existsByAuditoriumIdAndRowLabelAndSeatNumber(
            String auditoriumId,
            String rowLabel,
            Short seatNumber
    );

    long countByAuditoriumId(String auditoriumId);

    long countByAuditoriumIdAndStatus(String auditoriumId, SeatStatus status);
}