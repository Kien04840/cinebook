package com.cinebook.service.impl;

import com.cinebook.dto.response.TicketCheckInResponse;
import com.cinebook.dto.response.TicketVerifyResponse;
import com.cinebook.entity.*;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.enums.TicketStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.repository.TicketRepository;
import com.cinebook.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;

    @Override
    @Transactional(readOnly = true)
    public TicketVerifyResponse verifyTicket(String codeOrId) {
        if (!StringUtils.hasText(codeOrId)) {
            throw new BadRequestException("Mã vé hoặc QR code không được để trống.");
        }

        String query = codeOrId.trim();
        Ticket ticket = ticketRepository.findByQrCode(query)
                .or(() -> ticketRepository.findById(query))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé với mã hoặc QR code: " + query));

        Booking booking = ticket.getBooking();
        Showtime showtime = booking != null ? booking.getShowtime() : null;
        Movie movie = showtime != null ? showtime.getMovie() : null;
        Auditorium auditorium = showtime != null ? showtime.getAuditorium() : null;
        Cinema cinema = auditorium != null ? auditorium.getCinema() : null;
        Seat seat = ticket.getSeat();
        User customer = booking != null ? booking.getUser() : null;

        boolean isEligible = true;
        String ineligibleReason = null;

        if (ticket.getTicketStatus() == TicketStatus.USED) {
            isEligible = false;
            ineligibleReason = "Vé đã được sử dụng (đã soát vé trước đó).";
        } else if (ticket.getTicketStatus() == TicketStatus.CANCELLED) {
            isEligible = false;
            ineligibleReason = "Vé đã bị hủy (đơn hàng đã hoàn tiền hoặc bị hủy).";
        } else if (booking != null && booking.getBookingStatus() == BookingStatus.REFUNDED) {
            isEligible = false;
            ineligibleReason = "Đơn đặt vé đã hoàn tiền.";
        } else if (booking != null && booking.getBookingStatus() != BookingStatus.PAID) {
            isEligible = false;
            ineligibleReason = "Đơn đặt vé chưa hoàn tất thanh toán (trạng thái: " + booking.getBookingStatus() + ").";
        } else if (showtime != null && showtime.getStatus() == ShowtimeStatus.CANCELLED) {
            isEligible = false;
            ineligibleReason = "Suất chiếu đã bị hủy.";
        }

        return TicketVerifyResponse.builder()
                .ticketId(ticket.getId())
                .qrCode(ticket.getQrCode())
                .ticketPrice(ticket.getTicketPrice())
                .ticketStatus(ticket.getTicketStatus())
                .bookingId(booking != null ? booking.getId() : null)
                .bookingCode(booking != null ? booking.getBookingCode() : null)
                .customerName(customer != null ? customer.getFullName() : null)
                .customerEmail(customer != null ? customer.getEmail() : null)
                .movieTitle(movie != null ? movie.getTitle() : null)
                .moviePosterUrl(movie != null ? movie.getPosterUrl() : null)
                .cinemaName(cinema != null ? cinema.getName() : null)
                .auditoriumName(auditorium != null ? auditorium.getName() : null)
                .startTime(showtime != null ? showtime.getStartTime() : null)
                .endTime(showtime != null ? showtime.getEndTime() : null)
                .rowLabel(seat != null ? seat.getRowLabel() : null)
                .seatNumber(seat != null && seat.getSeatNumber() != null ? seat.getSeatNumber().intValue() : null)
                .seatCode(seat != null ? seat.getSeatCode() : null)
                .seatTypeName(seat != null && seat.getSeatType() != null ? seat.getSeatType().getName() : null)
                .checkInEligible(isEligible)
                .ineligibleReason(ineligibleReason)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketCheckInResponse checkInTicket(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            throw new BadRequestException("Mã vé không được để trống.");
        }

        String id = ticketId.trim();
        Ticket ticket = ticketRepository.findByIdWithLock(id)
                .or(() -> ticketRepository.findByQrCodeWithLock(id))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé với mã: " + id));

        if (ticket.getTicketStatus() == TicketStatus.USED) {
            throw new ConflictException("Vé này đã được soát trước đó. Không thể soát lại!");
        }

        if (ticket.getTicketStatus() == TicketStatus.CANCELLED) {
            throw new BadRequestException("Vé này đã bị hủy, không thể thực hiện soát vé.");
        }

        Booking booking = ticket.getBooking();
        if (booking == null || booking.getBookingStatus() != BookingStatus.PAID) {
            throw new BadRequestException("Đơn đặt vé chưa hoàn tất thanh toán hợp lệ hoặc đã hoàn tiền.");
        }

        ticket.setTicketStatus(TicketStatus.USED);
        Ticket saved = ticketRepository.saveAndFlush(ticket);

        log.info("Ticket {} (booking {}) checked in successfully by Box Office", saved.getId(), booking.getBookingCode());

        Showtime showtime = booking.getShowtime();
        Movie movie = showtime != null ? showtime.getMovie() : null;
        Auditorium auditorium = showtime != null ? showtime.getAuditorium() : null;

        return TicketCheckInResponse.builder()
                .ticketId(saved.getId())
                .qrCode(saved.getQrCode())
                .ticketStatus(saved.getTicketStatus())
                .checkedInAt(LocalDateTime.now())
                .message("Soát vé thành công! Chúc quý khách xem phim vui vẻ.")
                .bookingCode(booking.getBookingCode())
                .seatCode(saved.getSeat() != null ? saved.getSeat().getSeatCode() : null)
                .movieTitle(movie != null ? movie.getTitle() : null)
                .auditoriumName(auditorium != null ? auditorium.getName() : null)
                .startTime(showtime != null ? showtime.getStartTime() : null)
                .build();
    }
}
