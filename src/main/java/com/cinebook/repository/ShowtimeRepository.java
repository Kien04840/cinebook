package com.cinebook.repository;

import com.cinebook.entity.Showtime;
import com.cinebook.enums.ShowtimeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowtimeRepository extends JpaRepository<Showtime, String> {

    Page<Showtime> findByMovieId(
            String movieId,
            Pageable pageable
    );

    Page<Showtime> findByAuditoriumId(
            String auditoriumId,
            Pageable pageable
    );

    Page<Showtime> findByStatus(
            ShowtimeStatus status,
            Pageable pageable
    );

    Page<Showtime> findByMovieIdAndStatus(
            String movieId,
            ShowtimeStatus status,
            Pageable pageable
    );

    List<Showtime> findByMovieIdAndStartTimeBetween(
            String movieId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    List<Showtime> findByAuditoriumIdAndStartTimeBetween(
            String auditoriumId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    List<Showtime> findByStartTimeBetween(
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    List<Showtime> findByStatusAndStartTimeBefore(
            ShowtimeStatus status,
            LocalDateTime time
    );
}