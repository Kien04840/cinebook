package com.cinebook.repository;

import com.cinebook.entity.Showtime;
import com.cinebook.enums.ShowtimeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, String>, JpaSpecificationExecutor<Showtime> {

    @Override
    @EntityGraph(attributePaths = {"movie.movieGenres.genre", "auditorium.cinema"})
    Optional<Showtime> findById(String id);

    @Override
    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
    Page<Showtime> findAll(Specification<Showtime> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
    List<Showtime> findAll(Specification<Showtime> spec);

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

    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
    Page<Showtime> findByMovieId(String movieId, Pageable pageable);

    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
    Page<Showtime> findByAuditoriumId(String auditoriumId, Pageable pageable);

    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
    Page<Showtime> findByStatus(ShowtimeStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
    Page<Showtime> findByMovieIdAndStatus(String movieId, ShowtimeStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
    List<Showtime> findByMovieIdAndStartTimeBetween(
            String movieId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
    List<Showtime> findByAuditoriumIdAndStartTimeBetween(
            String auditoriumId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
    List<Showtime> findByStartTimeBetween(
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    @EntityGraph(attributePaths = {"movie", "auditorium.cinema"})
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

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        UPDATE Showtime s
        SET s.status = com.cinebook.enums.ShowtimeStatus.FINISHED,
            s.updatedAt = :now
        WHERE s.status = com.cinebook.enums.ShowtimeStatus.SCHEDULED
          AND s.endTime <= :now
    """)
    int markFinishedShowtimes(@Param("now") LocalDateTime now);

    boolean existsByAuditoriumId(String auditoriumId);

    @Query(value = """
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
    """, countQuery = """
        SELECT COUNT(s.id)
        FROM Showtime s
        JOIN s.auditorium a
        WHERE s.startTime >= :from AND s.startTime <= :to
          AND (:cinemaId IS NULL OR a.cinema.id = :cinemaId)
          AND (:movieId IS NULL OR s.movie.id = :movieId)
          AND s.status <> com.cinebook.enums.ShowtimeStatus.CANCELLED
    """)
    Page<Showtime> findActiveShowtimesPageForReport(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("cinemaId") String cinemaId,
            @Param("movieId") String movieId,
            Pageable pageable
    );

    @Query(value = """
        SELECT 
            COUNT(s.id) AS total_showtimes,
            COALESCE(SUM(aud_cap.cap), 0) AS total_capacity,
            COALESCE(SUM(occ.occupied_cap), 0) AS total_occupied
        FROM showtimes s
        JOIN (
            SELECT se.auditorium_id, SUM(st.capacity) AS cap
            FROM seats se
            JOIN seat_types st ON se.seat_type_id = st.id
            WHERE se.status = 'ACTIVE'
            GROUP BY se.auditorium_id
        ) aud_cap ON s.auditorium_id = aud_cap.auditorium_id
        LEFT JOIN (
            SELECT b.showtime_id, SUM(st.capacity) AS occupied_cap
            FROM showtimes st_in
            JOIN bookings b ON b.showtime_id = st_in.id
            JOIN tickets t ON t.booking_id = b.id AND t.ticket_status IN ('VALID', 'USED')
            JOIN seats se ON t.seat_id = se.id
            JOIN seat_types st ON se.seat_type_id = st.id
            WHERE st_in.start_time >= :from AND st_in.start_time <= :to
              AND st_in.status <> 'CANCELLED'
            GROUP BY b.showtime_id
        ) occ ON s.id = occ.showtime_id
        WHERE s.start_time >= :from AND s.start_time <= :to
          AND s.status <> 'CANCELLED'
    """, nativeQuery = true)
    Object[] findOperationsKpiNative(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = """
        SELECT 
            s.id AS showtime_id,
            m.id AS movie_id,
            m.title AS movie_title,
            c.id AS cinema_id,
            c.name AS cinema_name,
            a.id AS auditorium_id,
            a.name AS auditorium_name,
            s.start_time AS start_time,
            s.end_time AS end_time,
            s.format AS format,
            aud_cap.cap AS total_capacity,
            COALESCE(occ.occupied_cap, 0) AS occupied_seats,
            GREATEST(0, aud_cap.cap - COALESCE(occ.occupied_cap, 0)) AS available_seats,
            ROUND(LEAST(100.0, GREATEST(0.0, COALESCE(occ.occupied_cap, 0) / aud_cap.cap * 100)), 2) AS occupancy_rate
        FROM showtimes s
        JOIN movies m ON s.movie_id = m.id
        JOIN auditoriums a ON s.auditorium_id = a.id
        JOIN cinemas c ON a.cinema_id = c.id
        JOIN (
            SELECT se.auditorium_id, SUM(st.capacity) AS cap
            FROM seats se
            JOIN seat_types st ON se.seat_type_id = st.id
            WHERE se.status = 'ACTIVE'
            GROUP BY se.auditorium_id
        ) aud_cap ON aud_cap.auditorium_id = s.auditorium_id
        LEFT JOIN (
            SELECT b.showtime_id, SUM(st.capacity) AS occupied_cap
            FROM showtimes st_in
            JOIN bookings b ON b.showtime_id = st_in.id
            JOIN tickets t ON t.booking_id = b.id AND t.ticket_status IN ('VALID', 'USED')
            JOIN seats se ON t.seat_id = se.id
            JOIN seat_types st ON se.seat_type_id = st.id
            WHERE st_in.start_time >= :from AND st_in.start_time <= :to
              AND st_in.status <> 'CANCELLED'
            GROUP BY b.showtime_id
        ) occ ON occ.showtime_id = s.id
        WHERE s.start_time >= :from AND s.start_time <= :to
          AND s.status <> 'CANCELLED'
          AND (:cinemaId IS NULL OR c.id = :cinemaId)
          AND (:movieId IS NULL OR m.id = :movieId)
        ORDER BY occupancy_rate DESC, s.start_time ASC
        LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<Object[]> findShowtimeOccupanciesNative(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("cinemaId") String cinemaId,
            @Param("movieId") String movieId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = """
        SELECT COUNT(s.id)
        FROM showtimes s
        JOIN auditoriums a ON s.auditorium_id = a.id
        WHERE s.start_time >= :from AND s.start_time <= :to
          AND s.status <> 'CANCELLED'
          AND (:cinemaId IS NULL OR a.cinema_id = :cinemaId)
          AND (:movieId IS NULL OR s.movie_id = :movieId)
    """, nativeQuery = true)
    long countActiveShowtimesForReport(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("cinemaId") String cinemaId,
            @Param("movieId") String movieId
    );

    boolean existsByMovieId(String movieId);

    @Query("""
        SELECT COUNT(s) > 0
        FROM Showtime s
        WHERE s.movie.id = :movieId
          AND s.status = :status
          AND s.startTime > :startTime
    """)
    boolean existsByMovieIdAndStatusAndStartTimeAfter(
            @Param("movieId") String movieId,
            @Param("status") ShowtimeStatus status,
            @Param("startTime") LocalDateTime startTime
    );

    @Query("""
        SELECT COUNT(s) > 0
        FROM Showtime s
        WHERE s.auditorium.cinema.id = :cinemaId
          AND s.status = :status
          AND s.startTime > :startTime
    """)
    boolean existsByCinemaIdAndStatusAndStartTimeAfter(
            @Param("cinemaId") String cinemaId,
            @Param("status") ShowtimeStatus status,
            @Param("startTime") LocalDateTime startTime
    );

    @Query("""
        SELECT COUNT(s) > 0
        FROM Showtime s
        WHERE s.auditorium.id = :auditoriumId
          AND s.status = :status
          AND s.startTime > :startTime
    """)
    boolean existsByAuditoriumIdAndStatusAndStartTimeAfter(
            @Param("auditoriumId") String auditoriumId,
            @Param("status") ShowtimeStatus status,
            @Param("startTime") LocalDateTime startTime
    );
}