package com.cinebook.service.impl;

import com.cinebook.dto.request.BookingCheckInRequest;
import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.*;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.*;
import com.cinebook.mapper.BookingMapper;
import com.cinebook.mapper.PromotionMapper;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.BookingService;
import com.cinebook.service.EmailService;
import com.cinebook.service.PromotionService;
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
    private final PromotionRepository promotionRepository;
    private final BookingPromotionRepository bookingPromotionRepository;
    private final PromotionService promotionService;
    private final BookingMapper bookingMapper;
    private final PromotionMapper promotionMapper;
    private final EmailService emailService;


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

        if (showtime.getStatus() != ShowtimeStatus.SCHEDULED) {
            if (showtime.getStatus() == ShowtimeStatus.CANCELLED) {
                throw new BadRequestException("Lịch chiếu đã bị hủy.");
            }
            if (showtime.getStatus() == ShowtimeStatus.FINISHED) {
                throw new BadRequestException("Lịch chiếu đã kết thúc.");
            }
            throw new BadRequestException("Lịch chiếu hiện không khả dụng để đặt vé.");
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

        // Check if current user already has an active PENDING_PAYMENT booking for this showtime with exact same seats (Idempotency)
        List<Booking> activeUserBookings = bookingRepository.findActiveBookingsByUserAndShowtime(user.getId(), showtime.getId(), now);
        for (Booking activeB : activeUserBookings) {
            List<SeatHold> userHolds = seatHoldRepository.findByBookingId(activeB.getId());
            Set<String> userHeldSeatIds = userHolds.stream().map(h -> h.getSeat().getId()).collect(Collectors.toSet());
            if (userHeldSeatIds.equals(uniqueSeatIds)) {
                log.info("User {} is requesting booking for their existing active booking {}. Returning existing booking.", user.getId(), activeB.getId());
                List<BookingSeatResponse> existingSeatResponses = buildBookingSeatResponses(activeB);
                List<PaymentSummaryResponse> existingPaymentResponses = paymentRepository.findByBookingId(activeB.getId())
                        .stream()
                        .map(bookingMapper::toPaymentSummaryResponse)
                        .toList();
                BookingPromotionResponse existingPromoResponse = bookingPromotionRepository.findFirstByBookingId(activeB.getId())
                        .map(promotionMapper::toBookingPromotionResponse)
                        .orElse(null);
                return bookingMapper.toBookingDetailResponse(activeB, existingSeatResponses, Collections.emptyList(), existingPaymentResponses, existingPromoResponse);
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
        BigDecimal grossAmount = BigDecimal.ZERO;
        List<BookingSeatResponse> seatResponses = new ArrayList<>();

        for (Seat seat : seats) {
            BigDecimal modifier = (seat.getSeatType() != null && seat.getSeatType().getPriceModifier() != null)
                    ? seat.getSeatType().getPriceModifier()
                    : BigDecimal.ZERO;
            BigDecimal price = basePrice.add(modifier);
            grossAmount = grossAmount.add(price);
            seatResponses.add(bookingMapper.toBookingSeatResponse(seat, price));
        }

        Promotion appliedPromotion = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (StringUtils.hasText(request.getPromotionCode())) {
            String normalizedCode = request.getPromotionCode().trim().toUpperCase();
            Promotion promo = promotionRepository.findByCodeWithLock(normalizedCode)
                    .orElseThrow(() -> new BadRequestException("Mã giảm giá không tồn tại: " + normalizedCode));

            if (promo.getStatus() != PromotionStatus.ACTIVE) {
                throw new BadRequestException("Mã giảm giá hiện đang tạm khóa hoặc không hoạt động.");
            }

            if (now.isBefore(promo.getStartAt())) {
                throw new BadRequestException("Mã giảm giá chưa đến thời gian áp dụng.");
            }

            if (now.isAfter(promo.getEndAt()) || now.isEqual(promo.getEndAt())) {
                throw new BadRequestException("Mã giảm giá đã hết hạn sử dụng.");
            }

            if (promo.getMinOrderAmount() != null && grossAmount.compareTo(promo.getMinOrderAmount()) < 0) {
                throw new BadRequestException("Đơn đặt vé chưa đạt giá trị tối thiểu (" + promo.getMinOrderAmount() + " VND) để áp dụng mã giảm giá.");
            }

            if (promo.getUsageLimit() != null && promo.getUsedCount() >= promo.getUsageLimit()) {
                throw new ConflictException("Mã giảm giá đã hết lượt sử dụng.");
            }

            discountAmount = promotionService.calculateDiscount(promo, grossAmount);
            promo.setUsedCount(promo.getUsedCount() + 1);
            promotionRepository.save(promo);
            appliedPromotion = promo;
        }

        BigDecimal netTotal = grossAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        LocalDateTime holdExpiresAt = now.plusMinutes(HOLD_DURATION_MINUTES);
        String bookingCode = generateUniqueBookingCode(now);

        Booking booking = new Booking();
        booking.setBookingCode(bookingCode);
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setTotalAmount(netTotal);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(holdExpiresAt);

        Booking savedBooking;
        try {
            savedBooking = bookingRepository.saveAndFlush(booking);

            if (appliedPromotion != null) {
                BookingPromotion bookingPromotion = new BookingPromotion();
                BookingPromotionId bpId = new BookingPromotionId(appliedPromotion.getId(), savedBooking.getId());
                bookingPromotion.setId(bpId);
                bookingPromotion.setPromotion(appliedPromotion);
                bookingPromotion.setBooking(savedBooking);
                bookingPromotion.setDiscountAmount(discountAmount);
                bookingPromotion.setCreatedAt(now);
                bookingPromotionRepository.saveAndFlush(bookingPromotion);
            }

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
            log.warn("Concurrency conflict while holding seats or applying promotion for showtime {}: {}", showtime.getId(), ex.getMessage());
            throw new ConflictException("Một hoặc nhiều ghế đã được người khác giữ chỗ hoặc phát sinh xung đột dữ liệu.");
        }

        BookingPromotionResponse promoResponse = (appliedPromotion != null)
                ? promotionMapper.toBookingPromotionResponse(appliedPromotion, discountAmount)
                : null;

        return bookingMapper.toBookingDetailResponse(savedBooking, seatResponses, Collections.emptyList(), Collections.emptyList(), promoResponse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Booking expireBookingIfHoldExpired(Booking booking) {
        if (booking == null) {
            return null;
        }
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            return booking;
        }

        // Acquire pessimistic write lock and refresh latest DB state if ID is present
        Booking targetBooking = booking;
        if (booking.getId() != null) {
            targetBooking = bookingRepository.findByIdWithLock(booking.getId()).orElse(booking);
        }

        // Re-check after locking to guarantee idempotency and avoid race conditions
        if (targetBooking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            return targetBooking;
        }

        LocalDateTime now = LocalDateTime.now();
        if (targetBooking.getHoldExpiresAt() != null && !targetBooking.getHoldExpiresAt().isAfter(now)) {
            targetBooking.setBookingStatus(BookingStatus.EXPIRED);
            Booking saved = bookingRepository.save(targetBooking);
            Booking updatedBooking = (saved != null) ? saved : targetBooking;

            // Preserve seat history for expired booking before holds are deleted
            snapshotHeldSeatsAsCancelledTickets(updatedBooking);

            seatHoldRepository.deleteByBookingId(targetBooking.getId());
            releasePromotionQuotaIfApplied(targetBooking.getId());

            // Cancel any active pending payment attempts while strictly preserving terminal payment states
            cancelPendingPaymentsForBooking(targetBooking.getId());

            log.info("Successfully expired booking: code={}, id={}", updatedBooking.getBookingCode(), updatedBooking.getId());
            return updatedBooking;
        }
        return targetBooking;
    }

    private void cancelPendingPaymentsForBooking(String bookingId) {
        List<Payment> pendingPayments = paymentRepository.findByBookingId(bookingId).stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PENDING)
                .toList();
        for (Payment p : pendingPayments) {
            p.setPaymentStatus(PaymentStatus.CANCELLED);
        }
        if (!pendingPayments.isEmpty()) {
            paymentRepository.saveAll(pendingPayments);
            log.info("Cancelled {} pending payment(s) for booking {}", pendingPayments.size(), bookingId);
        }
    }

    private void releasePromotionQuotaIfApplied(String bookingId) {
        List<BookingPromotion> bookingPromotions = bookingPromotionRepository.findByBookingId(bookingId);
        for (BookingPromotion bp : bookingPromotions) {
            Promotion promo = promotionRepository.findByIdWithLock(bp.getPromotion().getId()).orElse(null);
            if (promo != null && promo.getUsedCount() > 0) {
                promo.setUsedCount(promo.getUsedCount() - 1);
                promotionRepository.save(promo);
                log.info("Released promotion quota for promo {}: new usedCount={}", promo.getCode(), promo.getUsedCount());
            }
        }
    }

    private void snapshotHeldSeatsAsCancelledTickets(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        List<Ticket> existingTickets = ticketRepository.findByBookingId(booking.getId());
        if (!existingTickets.isEmpty()) {
            return;
        }

        List<SeatHold> holds = seatHoldRepository.findByBookingId(booking.getId());
        if (holds.isEmpty()) {
            return;
        }

        BigDecimal basePrice = (booking.getShowtime() != null && booking.getShowtime().getBasePrice() != null)
                ? booking.getShowtime().getBasePrice()
                : BigDecimal.ZERO;

        List<Ticket> cancelledTickets = new ArrayList<>();
        for (SeatHold hold : holds) {
            Seat seat = hold.getSeat();
            BigDecimal modifier = (seat != null && seat.getSeatType() != null && seat.getSeatType().getPriceModifier() != null)
                    ? seat.getSeatType().getPriceModifier()
                    : BigDecimal.ZERO;

            String ticketId = UUID.randomUUID().toString();
            Ticket ticket = new Ticket();
            ticket.setId(ticketId);
            ticket.setBooking(booking);
            ticket.setSeat(seat);
            ticket.setTicketPrice(basePrice.add(modifier));
            ticket.setTicketStatus(TicketStatus.CANCELLED);
            ticket.setQrCode(ticketId);
            cancelledTickets.add(ticket);
        }

        try {
            ticketRepository.saveAllAndFlush(cancelledTickets);
            log.info("Saved {} cancelled ticket snapshot(s) for booking {}", cancelledTickets.size(), booking.getBookingCode());
        } catch (Exception ex) {
            log.warn("Failed to save cancelled ticket snapshots for booking {}: {}", booking.getBookingCode(), ex.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredSeatHolds(LocalDateTime now) {
        return seatHoldRepository.deleteExpiredHolds(now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse getBookingDetail(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với id: " + bookingId));

        validateBookingOwnershipOrAdmin(booking);

        if (booking.getBookingStatus() == BookingStatus.PENDING_PAYMENT) {
            booking = expireBookingIfHoldExpired(booking);
        }

        List<BookingSeatResponse> seatResponses = buildBookingSeatResponses(booking);
        List<TicketResponse> ticketResponses = ticketRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toTicketResponse)
                .toList();
        List<PaymentSummaryResponse> paymentResponses = paymentRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toPaymentSummaryResponse)
                .toList();
        BookingPromotionResponse promoResponse = bookingPromotionRepository.findFirstByBookingId(booking.getId())
                .map(promotionMapper::toBookingPromotionResponse)
                .orElse(null);

        return bookingMapper.toBookingDetailResponse(booking, seatResponses, ticketResponses, paymentResponses, promoResponse);
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
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> getAdminBookings(String q, BookingStatus status, String showtimeId, Pageable pageable) {
        String keyword = StringUtils.hasText(q) ? q.trim() : null;
        String stId = StringUtils.hasText(showtimeId) ? showtimeId.trim() : null;
        Page<Booking> page = bookingRepository.findAdminBookings(keyword, status, stId, pageable);
        return PageResponse.of(page, bookingMapper::toBookingSummaryResponse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse cancelBooking(String bookingId, CancelBookingRequest request) {
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .or(() -> bookingRepository.findById(bookingId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với id: " + bookingId));

        validateBookingOwnershipOrAdmin(booking);

        if (booking.getBookingStatus() == BookingStatus.PAID) {
            throw new BadRequestException("Không thể tự hủy đơn đặt vé đã thanh toán thành công. Vui lòng liên hệ quản trị viên.");
        }

        if (booking.getBookingStatus() == BookingStatus.CANCELLED || booking.getBookingStatus() == BookingStatus.EXPIRED) {
            throw new BadRequestException("Đơn đặt vé đã ở trạng thái " + booking.getBookingStatus() + " và không thể hủy.");
        }

        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Chỉ có thể hủy đơn đặt vé đang chờ thanh toán.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (booking.getHoldExpiresAt() != null && !booking.getHoldExpiresAt().isAfter(now)) {
            expireBookingIfHoldExpired(booking);
            throw new BadRequestException("Đơn đặt vé đã hết hạn giữ chỗ và không thể hủy.");
        }

        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
        User cancellingUser = userRepository.findById(currentUser.getId()).orElse(null);

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancelledByUser(cancellingUser);
        if (request != null && StringUtils.hasText(request.getReason())) {
            booking.setCancelledReason(request.getReason().trim());
        }

        Booking saved = bookingRepository.save(booking);
        Booking updatedBooking = (saved != null) ? saved : booking;

        // Preserve seat history for cancelled booking before holds are deleted
        snapshotHeldSeatsAsCancelledTickets(updatedBooking);

        seatHoldRepository.deleteByBookingId(booking.getId());

        // Idempotent quota release for PENDING_PAYMENT booking cancellation
        releasePromotionQuotaIfApplied(booking.getId());

        // Cancel any pending payment attempts for this booking
        cancelPendingPaymentsForBooking(booking.getId());

        List<BookingSeatResponse> seatResponses = buildBookingSeatResponses(booking);
        List<TicketResponse> ticketResponses = ticketRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toTicketResponse)
                .toList();
        List<PaymentSummaryResponse> paymentResponses = paymentRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toPaymentSummaryResponse)
                .toList();
        BookingPromotionResponse promoResponse = bookingPromotionRepository.findFirstByBookingId(booking.getId())
                .map(promotionMapper::toBookingPromotionResponse)
                .orElse(null);

        return bookingMapper.toBookingDetailResponse(booking, seatResponses, ticketResponses, paymentResponses, promoResponse);
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

        // Dispatch booking confirmation email asynchronously / safely
        try {
            String customerEmail = updatedBooking.getUser() != null ? updatedBooking.getUser().getEmail() : null;
            String customerName = updatedBooking.getUser() != null ? updatedBooking.getUser().getFullName() : null;
            if (customerEmail != null) {
                emailService.sendBookingConfirmationEmail(customerEmail, customerName, updatedBooking, createdTickets);
            }
        } catch (Exception e) {
            log.error("Failed to trigger booking confirmation email for booking {}: {}", updatedBooking.getBookingCode(), e.getMessage());
        }

        List<BookingSeatResponse> seatResponses = buildBookingSeatResponses(updatedBooking);
        List<TicketResponse> ticketResponses = createdTickets.stream()
                .map(bookingMapper::toTicketResponse)
                .toList();
        List<PaymentSummaryResponse> paymentResponses = paymentRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toPaymentSummaryResponse)
                .toList();
        BookingPromotionResponse promoResponse = bookingPromotionRepository.findFirstByBookingId(updatedBooking.getId())
                .map(promotionMapper::toBookingPromotionResponse)
                .orElse(null);

        return bookingMapper.toBookingDetailResponse(updatedBooking, seatResponses, ticketResponses, paymentResponses, promoResponse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse processBookingRefund(String bookingId, String reason, String userId) {
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với id: " + bookingId));

        if (booking.getBookingStatus() == BookingStatus.REFUNDED) {
            return getBookingDetail(booking.getId());
        }

        if (booking.getBookingStatus() != BookingStatus.PAID && booking.getBookingStatus() != BookingStatus.EXPIRED) {
            throw new BadRequestException("Đơn đặt vé đang ở trạng thái " + booking.getBookingStatus() + ", không thể hoàn tiền.");
        }

        List<Ticket> tickets = ticketRepository.findByBookingId(booking.getId());
        boolean hasUsedTickets = tickets.stream().anyMatch(t -> t.getTicketStatus() == TicketStatus.USED);
        if (hasUsedTickets) {
            throw new BadRequestException("Không thể hoàn tiền cho đơn hàng đã được sử dụng để vào rạp.");
        }

        booking.setBookingStatus(BookingStatus.REFUNDED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelledReason(StringUtils.hasText(reason) ? reason : "Hoàn tiền đơn đặt vé");

        if (StringUtils.hasText(userId)) {
            userRepository.findById(userId).ifPresent(booking::setCancelledByUser);
        }

        Booking updatedBooking = bookingRepository.save(booking);

        for (Ticket ticket : tickets) {
            ticket.setTicketStatus(TicketStatus.CANCELLED);
        }
        if (!tickets.isEmpty()) {
            ticketRepository.saveAll(tickets);
        }

        seatHoldRepository.deleteByBookingId(booking.getId());

        log.info("Successfully processed refund for booking {}: status=REFUNDED, tickets cancelled={}",
                booking.getId(), tickets.size());

        List<BookingSeatResponse> seatResponses = buildBookingSeatResponses(updatedBooking);
        List<TicketResponse> ticketResponses = tickets.stream()
                .map(bookingMapper::toTicketResponse)
                .toList();
        List<PaymentSummaryResponse> paymentResponses = paymentRepository.findByBookingId(booking.getId())
                .stream()
                .map(bookingMapper::toPaymentSummaryResponse)
                .toList();
        BookingPromotionResponse promoResponse = bookingPromotionRepository.findFirstByBookingId(updatedBooking.getId())
                .map(promotionMapper::toBookingPromotionResponse)
                .orElse(null);

        return bookingMapper.toBookingDetailResponse(updatedBooking, seatResponses, ticketResponses, paymentResponses, promoResponse);
    }



    @Override
    @Transactional(readOnly = true)
    public List<ShowtimeSeatStatusResponse> getShowtimeSeatAvailability(String showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch chiếu với id: " + showtimeId));

        Auditorium auditorium = showtime.getAuditorium();
        List<Seat> seats = seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(auditorium.getId());
        LocalDateTime now = LocalDateTime.now();

        Optional<UserDetailsImpl> currentUserOpt = SecurityUtils.getCurrentUserDetails();
        String currentUserId = currentUserOpt.map(UserDetailsImpl::getId).orElse(null);

        List<SeatHold> activeHolds = seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(showtimeId, now);
        Set<String> heldSeatIds = new HashSet<>();
        Set<String> currentUserHeldSeatIds = new HashSet<>();

        for (SeatHold sh : activeHolds) {
            heldSeatIds.add(sh.getSeat().getId());
            if (currentUserId != null && sh.getBooking() != null && sh.getBooking().getUser() != null) {
                if (currentUserId.equals(sh.getBooking().getUser().getId())) {
                    currentUserHeldSeatIds.add(sh.getSeat().getId());
                }
            }
        }

        List<Ticket> soldTickets = ticketRepository.findTicketsByShowtimeIdAndStatuses(showtimeId, SOLD_TICKET_STATUSES);
        Set<String> soldSeatIds = soldTickets.stream()
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        boolean isAuditoriumBlocked = auditorium.getStatus() != AuditoriumStatus.ACTIVE || auditorium.getDeletedAt() != null;
        boolean isShowtimeCancelled = showtime.getStatus() == ShowtimeStatus.CANCELLED;

        List<ShowtimeSeatStatusResponse> responses = new ArrayList<>();
        for (Seat seat : seats) {
            SeatAvailabilityStatus availabilityStatus;
            boolean isHeldByCurrentUser = false;

            if (isAuditoriumBlocked || isShowtimeCancelled || seat.getStatus() != SeatStatus.ACTIVE) {
                availabilityStatus = SeatAvailabilityStatus.BLOCKED;
            } else if (soldSeatIds.contains(seat.getId())) {
                availabilityStatus = SeatAvailabilityStatus.SOLD;
            } else if (heldSeatIds.contains(seat.getId())) {
                availabilityStatus = SeatAvailabilityStatus.HELD;
                isHeldByCurrentUser = currentUserHeldSeatIds.contains(seat.getId());
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
                    .isHeldByCurrentUser(isHeldByCurrentUser)
                    .build());
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDetailResponse getActiveBookingForShowtime(String showtimeId) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        List<Booking> activeBookings = bookingRepository.findActiveBookingsByUserAndShowtime(currentUserId, showtimeId, now);
        if (activeBookings.isEmpty()) {
            return null;
        }

        Booking activeBooking = activeBookings.get(0);
        List<BookingSeatResponse> seatResponses = buildBookingSeatResponses(activeBooking);
        List<PaymentSummaryResponse> paymentResponses = paymentRepository.findByBookingId(activeBooking.getId())
                .stream()
                .map(bookingMapper::toPaymentSummaryResponse)
                .toList();
        BookingPromotionResponse promoResponse = bookingPromotionRepository.findFirstByBookingId(activeBooking.getId())
                .map(promotionMapper::toBookingPromotionResponse)
                .orElse(null);

        return bookingMapper.toBookingDetailResponse(activeBooking, seatResponses, Collections.emptyList(), paymentResponses, promoResponse);
    }

    private void validateBookingOwnershipOrAdmin(Booking booking) {
        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        boolean isAdmin = currentUser.isAdmin();

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

    @Override
    @Transactional(readOnly = true)
    public BookingVerifyResponse verifyBookingCheckIn(String checkInCode) {
        if (!StringUtils.hasText(checkInCode)) {
            throw new BadRequestException("Mã soát vé không được để trống.");
        }

        String code = checkInCode.trim();
        Booking booking = bookingRepository.findByCheckInCodeWithDetails(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với mã soát vé: " + code));

        List<Ticket> tickets = ticketRepository.findByBookingIdWithSeat(booking.getId());

        boolean isEligible = true;
        String ineligibleReason = null;

        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            isEligible = false;
            ineligibleReason = "Đơn đặt vé đã bị hủy.";
        } else if (booking.getBookingStatus() == BookingStatus.REFUNDED) {
            isEligible = false;
            ineligibleReason = "Đơn đặt vé đã hoàn tiền.";
        } else if (booking.getBookingStatus() == BookingStatus.EXPIRED) {
            isEligible = false;
            ineligibleReason = "Đơn đặt vé đã hết hạn thanh toán.";
        } else if (booking.getBookingStatus() != BookingStatus.PAID) {
            isEligible = false;
            ineligibleReason = "Đơn đặt vé chưa hoàn tất thanh toán (trạng thái: " + booking.getBookingStatus() + ").";
        } else if (booking.getShowtime() != null && booking.getShowtime().getStatus() == ShowtimeStatus.CANCELLED) {
            isEligible = false;
            ineligibleReason = "Suất chiếu đã bị hủy.";
        } else {
            long validCount = tickets.stream().filter(t -> t.getTicketStatus() == TicketStatus.VALID).count();
            long usedCount = tickets.stream().filter(t -> t.getTicketStatus() == TicketStatus.USED).count();
            if (validCount == 0) {
                isEligible = false;
                if (usedCount > 0) {
                    ineligibleReason = "Tất cả các vé trong đơn hàng đã được sử dụng trước đó.";
                } else {
                    ineligibleReason = "Không có vé hợp lệ để soát trong đơn hàng này.";
                }
            }
        }

        List<BookingTicketItemResponse> ticketItems = tickets.stream().map(t -> {
            Seat s = t.getSeat();
            return BookingTicketItemResponse.builder()
                    .ticketId(t.getId())
                    .seatCode(s != null ? s.getSeatCode() : null)
                    .rowLabel(s != null ? s.getRowLabel() : null)
                    .seatNumber(s != null && s.getSeatNumber() != null ? s.getSeatNumber().intValue() : null)
                    .seatTypeName(s != null && s.getSeatType() != null ? s.getSeatType().getName() : null)
                    .ticketPrice(t.getTicketPrice())
                    .ticketStatus(t.getTicketStatus())
                    .build();
        }).toList();

        Showtime showtime = booking.getShowtime();
        Movie movie = showtime != null ? showtime.getMovie() : null;
        Auditorium auditorium = showtime != null ? showtime.getAuditorium() : null;
        Cinema cinema = auditorium != null ? auditorium.getCinema() : null;
        User customer = booking.getUser();

        int totalTickets = tickets.size();
        int validTickets = (int) tickets.stream().filter(t -> t.getTicketStatus() == TicketStatus.VALID).count();
        int usedTickets = (int) tickets.stream().filter(t -> t.getTicketStatus() == TicketStatus.USED).count();

        return BookingVerifyResponse.builder()
                .bookingId(booking.getId())
                .bookingCode(booking.getBookingCode())
                .checkInCode(booking.getCheckInCode())
                .bookingStatus(booking.getBookingStatus())
                .customerName(customer != null ? customer.getFullName() : null)
                .customerEmail(customer != null ? customer.getEmail() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .movieTitle(movie != null ? movie.getTitle() : null)
                .moviePosterUrl(movie != null ? movie.getPosterUrl() : null)
                .cinemaName(cinema != null ? cinema.getName() : null)
                .auditoriumName(auditorium != null ? auditorium.getName() : null)
                .startTime(showtime != null ? showtime.getStartTime() : null)
                .endTime(showtime != null ? showtime.getEndTime() : null)
                .tickets(ticketItems)
                .totalTickets(totalTickets)
                .validTickets(validTickets)
                .usedTickets(usedTickets)
                .checkInEligible(isEligible)
                .ineligibleReason(ineligibleReason)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingCheckInResponse checkInBooking(BookingCheckInRequest request) {
        if (request == null || !StringUtils.hasText(request.getCheckInCode())) {
            throw new BadRequestException("Mã soát vé không được để trống.");
        }

        String code = request.getCheckInCode().trim();

        // 1. Lock parent booking first to maintain consistent global lock ordering (Booking -> Ticket)
        Booking booking = bookingRepository.findByCheckInCodeWithLock(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với mã soát vé: " + code));

        // 2. Lock tickets for this booking
        List<Ticket> tickets = ticketRepository.findByBookingIdWithLock(booking.getId());

        // 3. Validate booking & showtime status
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Đơn đặt vé đã bị hủy, không thể thực hiện soát vé.");
        }
        if (booking.getBookingStatus() == BookingStatus.REFUNDED) {
            throw new BadRequestException("Đơn đặt vé đã hoàn tiền, không thể thực hiện soát vé.");
        }
        if (booking.getBookingStatus() == BookingStatus.EXPIRED) {
            throw new BadRequestException("Đơn đặt vé đã hết hạn thanh toán.");
        }
        if (booking.getBookingStatus() != BookingStatus.PAID) {
            throw new BadRequestException("Đơn đặt vé chưa hoàn tất thanh toán (trạng thái: " + booking.getBookingStatus() + ").");
        }
        if (booking.getShowtime() != null && booking.getShowtime().getStatus() == ShowtimeStatus.CANCELLED) {
            throw new BadRequestException("Suất chiếu đã bị hủy.");
        }

        List<Ticket> validTickets = tickets.stream()
                .filter(t -> t.getTicketStatus() == TicketStatus.VALID)
                .toList();
        long alreadyUsedCount = tickets.stream()
                .filter(t -> t.getTicketStatus() == TicketStatus.USED)
                .count();

        if (validTickets.isEmpty()) {
            if (alreadyUsedCount > 0) {
                throw new ConflictException("Tất cả các vé trong đơn đặt này đã được soát trước đó. Không thể soát lại!");
            }
            throw new BadRequestException("Không có vé hợp lệ để soát trong đơn đặt vé này.");
        }

        for (Ticket t : validTickets) {
            t.setTicketStatus(TicketStatus.USED);
        }
        ticketRepository.saveAllAndFlush(validTickets);

        log.info("Booking {} (code: {}) checked in: {} ticket(s) marked USED, {} previously USED",
                booking.getId(), booking.getBookingCode(), validTickets.size(), alreadyUsedCount);

        List<Ticket> reloadedTickets = ticketRepository.findByBookingIdWithSeat(booking.getId());
        List<BookingTicketItemResponse> ticketItems = reloadedTickets.stream().map(t -> {
            Seat s = t.getSeat();
            return BookingTicketItemResponse.builder()
                    .ticketId(t.getId())
                    .seatCode(s != null ? s.getSeatCode() : null)
                    .rowLabel(s != null ? s.getRowLabel() : null)
                    .seatNumber(s != null && s.getSeatNumber() != null ? s.getSeatNumber().intValue() : null)
                    .seatTypeName(s != null && s.getSeatType() != null ? s.getSeatType().getName() : null)
                    .ticketPrice(t.getTicketPrice())
                    .ticketStatus(t.getTicketStatus())
                    .build();
        }).toList();

        Showtime showtime = booking.getShowtime();
        Movie movie = showtime != null ? showtime.getMovie() : null;
        Auditorium auditorium = showtime != null ? showtime.getAuditorium() : null;
        Cinema cinema = auditorium != null ? auditorium.getCinema() : null;

        String message;
        if (alreadyUsedCount > 0) {
            message = String.format("Soát thành công %d vé còn lại (trước đó đã soát %d vé).", validTickets.size(), alreadyUsedCount);
        } else {
            message = String.format("Soát vé thành công cho toàn bộ %d ghế trong đơn hàng!", validTickets.size());
        }

        return BookingCheckInResponse.builder()
                .bookingId(booking.getId())
                .bookingCode(booking.getBookingCode())
                .checkInCode(booking.getCheckInCode())
                .result(alreadyUsedCount > 0 ? "PARTIALLY_CHECKED_IN" : "CHECKED_IN")
                .checkedInAt(LocalDateTime.now())
                .message(message)
                .movieTitle(movie != null ? movie.getTitle() : null)
                .cinemaName(cinema != null ? cinema.getName() : null)
                .auditoriumName(auditorium != null ? auditorium.getName() : null)
                .startTime(showtime != null ? showtime.getStartTime() : null)
                .tickets(ticketItems)
                .totalTickets(reloadedTickets.size())
                .checkedInCount(validTickets.size())
                .alreadyUsedCount((int) alreadyUsedCount)
                .build();
    }
}
