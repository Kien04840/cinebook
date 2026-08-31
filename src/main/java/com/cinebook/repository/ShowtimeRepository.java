package com.cinebook.repository;

import com.cinebook.entity.Showtime;
import com.cinebook.enums.ShowtimeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowtimeRepository extends JpaRepository<Showtime, String>, JpaSpecificationExecutor<Showtime> {

    @Query("""
        SELECT COUNT(s) > 0
        FROM Showtime s
        WHERE s.auditorium.id = :auditoriumId
          AND s.status <> com.cinebook.enums.ShowtimeStatus.CANCELLED
          AND s.startTime < :endTime
          AND s.endTime > :startTime
          AND (:excludeShowtimeId IS NULL OR s.id <> :excludeShowtimeId)
    """)
    boolean hasOverlappingShowtime(
            @Param("auditoriumId") String auditoriumId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeShowtimeId") String excludeShowtimeId
    );

    Page<Showtime> findByMovieId(String movieId, Pageable pageable);

    Page<Showtime> findByAuditoriumId(String auditoriumId, Pageable pageable);

    Page<Showtime> findByStatus(ShowtimeStatus status, Pageable pageable);

    Page<Showtime> findByMovieIdAndStatus(String movieId, ShowtimeStatus status, Pageable pageable);

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

    @Query("""
        SELECT s
        FROM Showtime s
        JOIN FETCH s.movie
        JOIN FETCH s.auditorium a
        JOIN FETCH a.cinema
        WHERE s.auditorium.id = :auditoriumId
          AND s.status <> com.cinebook.enums.ShowtimeStatus.CANCELLED
          AND s.startTime >= :startTime
          AND s.startTime <= :endTime
        ORDER BY s.startTime ASC
    """)
    List<Showtime> findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
            @Param("auditoriumId") String auditoriumId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("""
        SELECT s
        FROM Showtime s
        JOIN FETCH s.movie
        JOIN FETCH s.auditorium a
        JOIN FETCH a.cinema c
        WHERE c.id = :cinemaId
          AND s.startTime >= :fromTime
          AND s.startTime <= :toTime
        ORDER BY a.name ASC, s.startTime ASC
    """)
    List<Showtime> findCalendarShowtimes(
            @Param("cinemaId") String cinemaId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    boolean existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(
            String movieId,
            String auditoriumId,
            LocalDateTime startTime,
            ShowtimeStatus status
    );

    long countByStartTimeBetween(
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("""
        SELECT s
        FROM Showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.auditorium a
        JOIN FETCH a.cinema c
        WHERE s.startTime >= :from AND s.startTime <= :to
          AND (:cinemaId IS NULL OR c.id = :cinemaId)
          AND (:movieId IS NULL OR m.id = :movieId)
          AND s.status <> com.cinebook.enums.ShowtimeStatus.CANCELLED
        ORDER BY s.startTime ASC
    """)
    List<Showtime> findActiveShowtimesForReport(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("cinemaId") String cinemaId,
            @Param("movieId") String movieId
    );
}