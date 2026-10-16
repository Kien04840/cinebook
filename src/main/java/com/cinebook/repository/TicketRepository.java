package com.cinebook.repository;

import com.cinebook.entity.Ticket;
import com.cinebook.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, String> {

    List<Ticket> findByBookingId(String bookingId);

    @Query("""
        SELECT t FROM Ticket t
        JOIN FETCH t.seat s
        LEFT JOIN FETCH s.seatType st
        WHERE t.booking.id = :bookingId
        ORDER BY s.rowLabel ASC, s.seatNumber ASC
    """)
    List<Ticket> findByBookingIdWithSeat(@Param("bookingId") String bookingId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.booking.id = :bookingId")
    List<Ticket> findByBookingIdWithLock(@Param("bookingId") String bookingId);

    Optional<Ticket> findByQrCode(String qrCode);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdWithLock(@Param("id") String id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.qrCode = :qrCode")
    Optional<Ticket> findByQrCodeWithLock(@Param("qrCode") String qrCode);

    boolean existsByQrCode(String qrCode);

    List<Ticket> findBySeatId(String seatId);

    boolean existsBySeatId(String seatId);

    Page<Ticket> findByTicketStatus(
            TicketStatus ticketStatus,
            Pageable pageable
    );

    long countByBookingId(String bookingId);

    long countByBookingIdAndTicketStatus(
            String bookingId,
            TicketStatus ticketStatus
    );

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.booking.showtime.id = :showtimeId
          AND t.seat.id IN :seatIds
          AND t.ticketStatus = :status
    """)
    List<Ticket> findValidTicketsByShowtimeAndSeatIds(
            @Param("showtimeId") String showtimeId,
            @Param("seatIds") Collection<String> seatIds,
            @Param("status") TicketStatus status
    );

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.booking.showtime.id = :showtimeId
          AND t.seat.id IN :seatIds
          AND t.ticketStatus IN :statuses
    """)
    List<Ticket> findTicketsByShowtimeAndSeatIdsAndStatuses(
            @Param("showtimeId") String showtimeId,
            @Param("seatIds") Collection<String> seatIds,
            @Param("statuses") Collection<TicketStatus> statuses
    );

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.booking.showtime.id = :showtimeId
          AND t.ticketStatus = :status
    """)
    List<Ticket> findValidTicketsByShowtimeId(
            @Param("showtimeId") String showtimeId,
            @Param("status") TicketStatus status
    );

    @Query("""
        SELECT COUNT(t) > 0
        FROM Ticket t
        WHERE t.booking.showtime.id = :showtimeId
          AND t.ticketStatus IN (com.cinebook.enums.TicketStatus.VALID, com.cinebook.enums.TicketStatus.USED)
    """)
    boolean hasPaidOrUsedTickets(@Param("showtimeId") String showtimeId);

    @Query("""
        SELECT COUNT(t) > 0
        FROM Ticket t
        WHERE t.booking.showtime.id = :showtimeId
    """)
    boolean existsByShowtimeId(@Param("showtimeId") String showtimeId);

    @Query("""
        SELECT t FROM Ticket t
        JOIN FETCH t.seat s
        LEFT JOIN FETCH s.seatType st
        WHERE t.booking.showtime.id = :showtimeId
          AND t.ticketStatus IN :statuses
    """)
    List<Ticket> findTicketsByShowtimeIdAndStatuses(
            @Param("showtimeId") String showtimeId,
            @Param("statuses") Collection<TicketStatus> statuses
    );

    @Query("""
        SELECT COUNT(t)
        FROM Ticket t
        WHERE t.booking.bookingStatus IN (com.cinebook.enums.BookingStatus.PAID, com.cinebook.enums.BookingStatus.REFUNDED)
          AND t.createdAt >= :from AND t.createdAt <= :to
    """)
    long countGrossTicketsBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query("""
        SELECT COUNT(t)
        FROM Ticket t
        WHERE t.booking.bookingStatus = com.cinebook.enums.BookingStatus.REFUNDED
          AND t.ticketStatus = com.cinebook.enums.TicketStatus.CANCELLED
          AND t.createdAt >= :from AND t.createdAt <= :to
    """)
    long countRefundedTicketsBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query("""
        SELECT t
        FROM Ticket t
        JOIN FETCH t.booking b
        JOIN FETCH b.showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.auditorium a
        JOIN FETCH a.cinema c
        WHERE b.bookingStatus IN (com.cinebook.enums.BookingStatus.PAID, com.cinebook.enums.BookingStatus.REFUNDED)
          AND t.createdAt >= :from AND t.createdAt <= :to
    """)
    List<Ticket> findSoldTicketsBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query("""
        SELECT COUNT(t) > 0
        FROM Ticket t
        WHERE t.seat.auditorium.id = :auditoriumId
    """)
    boolean existsByAuditoriumId(@Param("auditoriumId") String auditoriumId);

    @Query("""
        SELECT COUNT(t) > 0
        FROM Ticket t
        WHERE t.seat.id = :seatId
          AND t.ticketStatus IN (com.cinebook.enums.TicketStatus.VALID, com.cinebook.enums.TicketStatus.USED)
          AND t.booking.showtime.status = com.cinebook.enums.ShowtimeStatus.SCHEDULED
          AND t.booking.showtime.endTime > :currentTime
    """)
    boolean existsUpcomingValidTicketsBySeatId(
            @Param("seatId") String seatId,
            @Param("currentTime") java.time.LocalDateTime currentTime
    );

    @Query(value = """
        SELECT DATE_FORMAT(t.created_at, :dateFormat) AS period,
               COUNT(t.id) AS ticket_count
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        WHERE b.booking_status IN ('PAID', 'REFUNDED')
          AND t.ticket_status <> 'CANCELLED'
          AND t.created_at >= :from AND t.created_at <= :to
        GROUP BY DATE_FORMAT(t.created_at, :dateFormat)
        ORDER BY period ASC
    """, nativeQuery = true)
    List<Object[]> findTicketTrendNative(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("dateFormat") String dateFormat
    );

    @Query(value = """
        SELECT m.id AS movie_id,
               COUNT(t.id) AS gross_tickets,
               COALESCE(SUM(CASE WHEN t.ticket_status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS refunded_tickets
        FROM movies m
        JOIN showtimes s ON s.movie_id = m.id
        JOIN bookings b ON b.showtime_id = s.id
        JOIN tickets t ON t.booking_id = b.id
        WHERE b.booking_status IN ('PAID', 'REFUNDED')
          AND t.created_at >= :from AND t.created_at <= :to
        GROUP BY m.id
    """, nativeQuery = true)
    List<Object[]> findMovieTicketReportNative(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query(value = """
        SELECT c.id AS cinema_id,
               COUNT(t.id) AS gross_tickets,
               COALESCE(SUM(CASE WHEN t.ticket_status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS refunded_tickets
        FROM cinemas c
        JOIN auditoriums a ON a.cinema_id = c.id
        JOIN showtimes s ON s.auditorium_id = a.id
        JOIN bookings b ON b.showtime_id = s.id
        JOIN tickets t ON t.booking_id = b.id
        WHERE b.booking_status IN ('PAID', 'REFUNDED')
          AND t.created_at >= :from AND t.created_at <= :to
        GROUP BY c.id
    """, nativeQuery = true)
    List<Object[]> findCinemaTicketReportNative(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query(value = """
        SELECT COUNT(t.id) AS gross_tickets,
               COALESCE(SUM(CASE WHEN b.booking_status = 'REFUNDED' AND t.ticket_status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS refunded_tickets
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        WHERE b.booking_status IN ('PAID', 'REFUNDED')
          AND t.created_at >= :from AND t.created_at <= :to
    """, nativeQuery = true)
    Object[] countTicketKpiBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query(value = """
        SELECT b.showtime_id AS showtime_id,
               COALESCE(SUM(st.capacity), 0) AS occupied_capacity
        FROM bookings b
        JOIN tickets t ON t.booking_id = b.id AND t.ticket_status IN ('VALID', 'USED')
        JOIN seats se ON t.seat_id = se.id
        JOIN seat_types st ON se.seat_type_id = st.id
        WHERE b.showtime_id IN (:showtimeIds)
        GROUP BY b.showtime_id
    """, nativeQuery = true)
    List<Object[]> sumOccupiedCapacityByShowtimeIds(
            @Param("showtimeIds") Collection<String> showtimeIds
    );

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        UPDATE tickets t
        JOIN bookings b ON t.booking_id = b.id
        JOIN showtimes s ON b.showtime_id = s.id
        SET t.ticket_status = 'EXPIRED'
        WHERE t.ticket_status = 'VALID'
          AND b.booking_status = 'PAID'
          AND s.end_time < :now
    """, nativeQuery = true)
    int expirePastTickets(@Param("now") java.time.LocalDateTime now);
}
