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

    Optional<Ticket> findByQrCode(String qrCode);

    boolean existsByQrCode(String qrCode);

    List<Ticket> findBySeatId(String seatId);

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
        SELECT t FROM Ticket t
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
}