package com.cinebook.service.impl;

import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.*;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.*;
import com.cinebook.mapper.BookingMapper;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.BookingService;
import com.cinebook.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final int MAX_SEATS_PER_BOOKING = 8;
    private static final int HOLD_DURATION_MINUTES = 5;
    private static final String CODE_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<TicketStatus> SOLD_TICKET_STATUSES = Set.of(TicketStatus.VALID, TicketStatus.USED);

    private final BookingRepository bookingRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final TicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BookingMapper bookingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse createBooking(CreateBookingRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            throw new BadRequestException("Danh sách ghế không được để trống.");
        }

        if (request.getSeatIds().size() > MAX_SEATS_PER_BOOKING) {
            throw new BadRequestException("Không thể đặt quá " + MAX_SEATS_PER_BOOKING + " ghế trong một lần đặt vé.");
        }

        Set<String> uniqueSeatIds = new LinkedHashSet<>(request.getSeatIds());
        if (uniqueSeatIds.size() != request.getSeatIds().size()) {
            throw new BadRequestException("Danh sách ghế không được chứa ghế trùng lặp.");
        }

        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch chiếu với id: " + request.getShowtimeId()));

        LocalDateTime now = LocalDateTime.now();

        if (showtime.getStatus() == ShowtimeStatus.CANCELLED) {
            throw new BadRequestException("Lịch chiếu đã bị hủy.");
        }

        if (showtime.getStartTime().isBefore(now)) {
            throw new BadRequestException("Lịch chiếu đã bắt đầu hoặc đã qua.");
        }

        Auditorium auditorium = showtime.getAuditorium();
        if (auditorium == null || auditorium.getStatus() != AuditoriumStatus.ACTIVE || auditorium.getDeletedAt() != null) {
            throw new ConflictException("Phòng chiếu hiện không hoạt động hoặc đang bảo trì.");
        }

        Cinema cinema = auditorium.getCinema();
        if (cinema == null || cinema.getStatus() != CinemaStatus.ACTIVE || cinema.getDeletedAt() != null) {
            throw new ConflictException("Rạp chiếu phim hiện không hoạt động.");
        }

        Movie movie = showtime.getMovie();
        if (movie == null || movie.getDeletedAt() != null) {
            throw new BadRequestException("Phim của lịch chiếu này không khả dụng.");
        }

        List<Seat> seats = seatRepository.findByIdIn(uniqueSeatIds);
        if (seats.size() != uniqueSeatIds.size()) {
            throw new ResourceNotFoundException("Một hoặc nhiều ghế không tồn tại.");
        }

        for (Seat seat : seats) {
            if (!seat.getAuditorium().getId().equals(auditorium.getId())) {
                throw new BadRequestException("Ghế " + seat.getSeatCode() + " không thuộc phòng chiếu của lịch chiếu này.");
            }
            if (seat.getStatus() != SeatStatus.ACTIVE) {
                throw new BadRequestException("Ghế " + seat.getSeatCode() + " đang gặp sự cố (BROKEN) và không thể đặt.");
            }
        }

        // Critical Audit #1: Actively resolve/delete expired SeatHolds for requested seats so they do not trigger uk_seat_holds_showtime_seat
        seatHoldRepository.deleteExpiredHoldsForSeats(showtime.getId(), uniqueSeatIds, now);

        List<SeatHold> activeHolds = seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(showtime.getId(), uniqueSeatIds, now);
        if (!activeHolds.isEmpty()) {
            throw new ConflictException("Một hoặc nhiều ghế đã được giữ chỗ bởi người khác. Vui lòng chọn ghế khác.");
        }

        // Critical Audit #2: Check sold tickets (both VALID and USED)
        List<Ticket> soldTickets = ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(showtime.getId(), uniqueSeatIds, SOLD_TICKET_STATUSES);
        if (!soldTickets.isEmpty()) {
            throw new ConflictException("Một hoặc nhiều ghế đã được bán. Vui lòng chọn ghế khác.");
        }

        BigDecimal basePrice = showtime.getBasePrice();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<BookingSeatResponse> seatResponses = new ArrayList<>();

        for (Seat seat : seats) {
            BigDecimal modifier = (seat.getSeatType() != null && seat.getSeatType().getPriceModifier() != null)
                    ? seat.getSeatType().getPriceModifier()
                    : BigDecimal.ZERO;
            BigDecimal price = basePrice.add(modifier);
            totalAmount = totalAmount.add(price);
            seatResponses.add(bookingMapper.toBookingSeatResponse(seat, price));
        }

        LocalDateTime holdExpiresAt = now.plusMinutes(HOLD_DURATION_MINUTES);
        String bookingCode = generateUniqueBookingCode(now);

        Booking booking = new Booking();
        booking.setBookingCode(bookingCode);
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setTotalAmount(totalAmount);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(holdExpiresAt);

        Booking savedBooking;
        try {
            savedBooking = bookingRepository.saveAndFlush(booking);

            List<SeatHold> seatHoldsToSave = new ArrayList<>();
            for (Seat seat : seats) {
                SeatHold seatHold = new SeatHold();
                seatHold.setShowtime(showtime);
                seatHold.setSeat(seat);
                seatHold.setBooking(savedBooking);
                seatHold.setExpiresAt(holdExpiresAt);
                seatHoldsToSave.add(seatHold);
            }
            seatHoldRepository.saveAllAndFlush(seatHoldsToSave);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrency conflict while holding seats for showtime {}: {}", showtime.getId(), ex.getMessage());
            throw new ConflictException("Một hoặc nhiều ghế đã được người khác giữ chỗ. Vui lòng chọn ghế khác.");
        }

        return bookingMapper.toBookingDetailResponse(savedBooking, seatResponses, Collections.emptyList(), Collections.emptyList());
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDetailResponse getBookingDetail(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với id: " + bookingId));

        validateBookingOwnershipOrAdmin(booking);

        List<BookingSeatResponse> seatResponses = buildBookingSeatResponses(booking);
        List<TicketResponse> ticketResponses = ticketRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toTicketResponse)
                .toList();
        List<PaymentSummaryResponse> paymentResponses = paymentRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toPaymentSummaryResponse)
                .toList();

        return bookingMapper.toBookingDetailResponse(booking, seatResponses, ticketResponses, paymentResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> getMyBookings(BookingStatus status, Pageable pageable) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        Page<Booking> page = (status != null)
                ? bookingRepository.findByUserIdAndBookingStatus(currentUserId, status, pageable)
                : bookingRepository.findByUserId(currentUserId, pageable);

        return PageResponse.of(page, bookingMapper::toBookingSummaryResponse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse cancelBooking(String bookingId, CancelBookingRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với id: " + bookingId));

        validateBookingOwnershipOrAdmin(booking);

        if (booking.getBookingStatus() == BookingStatus.PAID) {
            throw new BadRequestException("Không thể tự hủy đơn đặt vé đã thanh toán thành công. Vui lòng liên hệ quản trị viên.");
        }

        if (booking.getBookingStatus() == BookingStatus.CANCELLED || booking.getBookingStatus() == BookingStatus.EXPIRED) {
            throw new BadRequestException("Đơn đặt vé đã ở trạng thái " + booking.getBookingStatus() + " và không thể hủy.");
        }

        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
        User cancellingUser = userRepository.findById(currentUser.getId()).orElse(null);

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelledByUser(cancellingUser);
        if (request != null && StringUtils.hasText(request.getReason())) {
            booking.setCancelledReason(request.getReason().trim());
        }

        bookingRepository.save(booking);
        seatHoldRepository.deleteByBookingId(booking.getId());

        List<BookingSeatResponse> seatResponses = buildBookingSeatResponses(booking);
        List<TicketResponse> ticketResponses = ticketRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toTicketResponse)
                .toList();
        List<PaymentSummaryResponse> paymentResponses = paymentRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toPaymentSummaryResponse)
                .toList();

        return bookingMapper.toBookingDetailResponse(booking, seatResponses, ticketResponses, paymentResponses);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse confirmPaidBooking(String bookingId, String paymentId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với id: " + bookingId));

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán với id: " + paymentId));

        if (!payment.getBooking().getId().equals(booking.getId())) {
            throw new BadRequestException("Bản ghi thanh toán không thuộc về đơn đặt vé này.");
        }

        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException("Trạng thái thanh toán không hợp lệ (không phải SUCCESS).");
        }

        if (payment.getAmount().compareTo(booking.getTotalAmount()) != 0) {
            throw new BadRequestException("Số tiền thanh toán (" + payment.getAmount() + ") không khớp với tổng tiền đơn đặt vé (" + booking.getTotalAmount() + ").");
        }

        // Idempotency: If already PAID, return current state without creating duplicate tickets
        if (booking.getBookingStatus() == BookingStatus.PAID) {
            return getBookingDetail(booking.getId());
        }

        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Đơn đặt vé đang ở trạng thái " + booking.getBookingStatus() + ", không thể chuyển sang PAID.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(now)) {
            throw new BadRequestException("Đơn đặt vé đã hết hạn giữ chỗ, không thể xác nhận thanh toán.");
        }

        List<SeatHold> holds = seatHoldRepository.findByBookingId(booking.getId());
        if (holds.isEmpty()) {
            List<Ticket> existingTickets = ticketRepository.findByBookingId(booking.getId());
            if (!existingTickets.isEmpty()) {
                return getBookingDetail(booking.getId());
            }
            throw new BadRequestException("Không tìm thấy thông tin giữ chỗ cho đơn đặt vé này hoặc giữ chỗ đã hết hạn.");
        }

        booking.setBookingStatus(BookingStatus.PAID);
        Booking updatedBooking = bookingRepository.save(booking);

        List<Ticket> createdTickets = new ArrayList<>();
        BigDecimal basePrice = booking.getShowtime().getBasePrice();

        for (SeatHold hold : holds) {
            Seat seat = hold.getSeat();
            BigDecimal modifier = (seat.getSeatType() != null && seat.getSeatType().getPriceModifier() != null)
                    ? seat.getSeatType().getPriceModifier()
                    : BigDecimal.ZERO;
            BigDecimal ticketPrice = basePrice.add(modifier);

            String ticketId = UUID.randomUUID().toString();
            Ticket ticket = new Ticket();
            ticket.setId(ticketId);
            ticket.setBooking(updatedBooking);
            ticket.setSeat(seat);
            ticket.setTicketPrice(ticketPrice);
            ticket.setTicketStatus(TicketStatus.VALID);
            ticket.setQrCode(ticketId); // UUID as QR payload
            createdTickets.add(ticket);
        }

        try {
            ticketRepository.saveAllAndFlush(createdTickets);
        } catch (DataIntegrityViolationException ex) {
            log.error("Duplicate ticket creation attempt for booking {}: {}", booking.getId(), ex.getMessage());
            throw new ConflictException("Vé cho ghế này đã tồn tại.");
        }

        seatHoldRepository.deleteByBookingId(booking.getId());

        List<BookingSeatResponse> seatResponses = buildBookingSeatResponses(updatedBooking);
        List<TicketResponse> ticketResponses = createdTickets.stream()
                .map(bookingMapper::toTicketResponse)
                .toList();
        List<PaymentSummaryResponse> paymentResponses = paymentRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toPaymentSummaryResponse)
                .toList();

        return bookingMapper.toBookingDetailResponse(updatedBooking, seatResponses, ticketResponses, paymentResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowtimeSeatStatusResponse> getShowtimeSeatAvailability(String showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch chiếu với id: " + showtimeId));

        Auditorium auditorium = showtime.getAuditorium();
        List<Seat> seats = seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(auditorium.getId());
        LocalDateTime now = LocalDateTime.now();

        List<SeatHold> activeHolds = seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(showtimeId, now);
        Set<String> heldSeatIds = activeHolds.stream()
                .map(sh -> sh.getSeat().getId())
                .collect(Collectors.toSet());

        List<Ticket> soldTickets = ticketRepository.findTicketsByShowtimeIdAndStatuses(showtimeId, SOLD_TICKET_STATUSES);
        Set<String> soldSeatIds = soldTickets.stream()
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        boolean isAuditoriumBlocked = auditorium.getStatus() != AuditoriumStatus.ACTIVE || auditorium.getDeletedAt() != null;
        boolean isShowtimeCancelled = showtime.getStatus() == ShowtimeStatus.CANCELLED;

        List<ShowtimeSeatStatusResponse> responses = new ArrayList<>();
        for (Seat seat : seats) {
            SeatAvailabilityStatus availabilityStatus;

            if (isAuditoriumBlocked || isShowtimeCancelled || seat.getStatus() != SeatStatus.ACTIVE) {
                availabilityStatus = SeatAvailabilityStatus.BLOCKED;
            } else if (soldSeatIds.contains(seat.getId())) {
                availabilityStatus = SeatAvailabilityStatus.SOLD;
            } else if (heldSeatIds.contains(seat.getId())) {
                availabilityStatus = SeatAvailabilityStatus.HELD;
            } else {
                availabilityStatus = SeatAvailabilityStatus.AVAILABLE;
            }

            SeatType seatType = seat.getSeatType();
            responses.add(ShowtimeSeatStatusResponse.builder()
                    .id(seat.getId())
                    .auditoriumId(auditorium.getId())
                    .seatTypeId(seatType != null ? seatType.getId() : null)
                    .seatTypeName(seatType != null ? seatType.getName() : null)
                    .priceModifier(seatType != null ? seatType.getPriceModifier() : BigDecimal.ZERO)
                    .rowLabel(seat.getRowLabel())
                    .seatNumber(seat.getSeatNumber())
                    .seatCode(seat.getSeatCode())
                    .seatStatus(seat.getStatus())
                    .availabilityStatus(availabilityStatus)
                    .build());
        }

        return responses;
    }

    private void validateBookingOwnershipOrAdmin(Booking booking) {
        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !booking.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Bạn không có quyền truy cập đơn đặt vé này.");
        }
    }

    private List<BookingSeatResponse> buildBookingSeatResponses(Booking booking) {
        List<Ticket> tickets = ticketRepository.findByBookingId(booking.getId());
        if (!tickets.isEmpty()) {
            return tickets.stream()
                    .map(t -> bookingMapper.toBookingSeatResponse(t.getSeat(), t.getTicketPrice()))
                    .toList();
        }

        List<SeatHold> holds = seatHoldRepository.findByBookingId(booking.getId());
        if (!holds.isEmpty()) {
            BigDecimal basePrice = booking.getShowtime().getBasePrice();
            return holds.stream()
                    .map(h -> {
                        Seat seat = h.getSeat();
                        BigDecimal modifier = (seat.getSeatType() != null && seat.getSeatType().getPriceModifier() != null)
                                ? seat.getSeatType().getPriceModifier()
                                : BigDecimal.ZERO;
                        return bookingMapper.toBookingSeatResponse(seat, basePrice.add(modifier));
                    })
                    .toList();
        }

        return Collections.emptyList();
    }

    private String generateUniqueBookingCode(LocalDateTime now) {
        String datePrefix = "CB-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        String code;
        int attempts = 0;

        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = datePrefix + sb.toString();
            attempts++;
            if (attempts > 50) {
                code = datePrefix + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                break;
            }
        } while (bookingRepository.existsByBookingCode(code));

        return code;
    }
}
