package com.cinebook.repository;

import com.cinebook.entity.Ticket;
import com.cinebook.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}