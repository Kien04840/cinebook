package com.cinebook.service.impl;

import com.cinebook.dto.request.BookingCheckInRequest;
import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.*;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.*;
import com.cinebook.dto.request.BookingFoodItemRequest;
import com.cinebook.mapper.BookingMapper;
import com.cinebook.mapper.FoodItemMapper;
import com.cinebook.mapper.PromotionMapper;
import com.cinebook.repository.*;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.BookingService;
import com.cinebook.service.EmailService;
import com.cinebook.service.NotificationService;
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

/**
 * Dịch vụ lõi quản lý quy trình đặt vé và giữ chỗ xem phim (Booking & Seat Hold Engine).
 * 
 * Kiến trúc & Ràng buộc nghiệp vụ quan trọng (Business Invariants):
 * 1. Cơ chế Giữ chỗ tạm thời (Seat Hold):
 *    - Khi khách chọn ghế và tạo đơn, hệ thống cấp một khoảng thời gian giữ chỗ 5 phút (HOLD_DURATION_MINUTES = 5).
 *    - Bản ghi giữ chỗ được lưu trong bảng seat_holds với ràng buộc khóa duy nhất uk_seat_holds_showtime_seat (showtime_id, seat_id).
 *    - Sau 5 phút, giữ chỗ tự động hết hạn và được dọn dẹp bởi BookingCleanupTask hoặc cơ chế Lazy Expiration.
 * 2. Tính toàn vẹn của Đơn hàng (Atomic & Lock-safe):
 *    - Chống bán trùng ghế (Double-booking): Sử dụng Khóa bi quan (Pessimistic Write Lock: findByIdWithLock) trên Booking.
 *    - Đồng bộ đa luồng: Đảm bảo không xảy ra Race Condition khi nhiều khách hàng cùng chọn một ghế tại cùng một thời điểm.
 * 3. Quy tắc thanh toán & Khuyến mãi:
 *    - Tổng tiền Booking = (Tiền vé - Khuyến mãi) + Tiền bắp nước F&B (authoritative server-side calculation).
 *    - Khi đơn hàng hết hạn hoặc bị hủy, lượt dùng mã giảm giá (Promotion.usedCount) được tự động hoàn trả an toàn.
 */
@Slf4j
@Service
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
    private final com.cinebook.service.PricingService pricingService;
    private final FoodItemRepository foodItemRepository;
    private final BookingFoodRepository bookingFoodRepository;
    private final FoodItemMapper foodItemMapper;
    private final NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    public BookingServiceImpl(
            BookingRepository bookingRepository,
            SeatHoldRepository seatHoldRepository,
            TicketRepository ticketRepository,
            SeatRepository seatRepository,
            ShowtimeRepository showtimeRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            PromotionRepository promotionRepository,
            BookingPromotionRepository bookingPromotionRepository,
            PromotionService promotionService,
            BookingMapper bookingMapper,
            PromotionMapper promotionMapper,
            EmailService emailService,
            com.cinebook.service.PricingService pricingService,
            FoodItemRepository foodItemRepository,
            BookingFoodRepository bookingFoodRepository,
            FoodItemMapper foodItemMapper,
            NotificationService notificationService
    ) {
        this.bookingRepository = bookingRepository;
        this.seatHoldRepository = seatHoldRepository;
        this.ticketRepository = ticketRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.promotionRepository = promotionRepository;
        this.bookingPromotionRepository = bookingPromotionRepository;
        this.promotionService = promotionService;
        this.bookingMapper = bookingMapper;
        this.promotionMapper = promotionMapper;
        this.emailService = emailService;
        this.pricingService = (pricingService != null) ? pricingService : createFallbackPricingService();
        this.foodItemRepository = foodItemRepository;
        this.bookingFoodRepository = bookingFoodRepository;
        this.foodItemMapper = (foodItemMapper != null) ? foodItemMapper : new FoodItemMapper();
        this.notificationService = notificationService;
    }

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            SeatHoldRepository seatHoldRepository,
            TicketRepository ticketRepository,
            SeatRepository seatRepository,
            ShowtimeRepository showtimeRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            PromotionRepository promotionRepository,
            BookingPromotionRepository bookingPromotionRepository,
            PromotionService promotionService,
            BookingMapper bookingMapper,
            PromotionMapper promotionMapper,
            EmailService emailService,
            com.cinebook.service.PricingService pricingService,
            FoodItemRepository foodItemRepository,
            BookingFoodRepository bookingFoodRepository,
            FoodItemMapper foodItemMapper
    ) {
        this(bookingRepository, seatHoldRepository, ticketRepository, seatRepository,
                showtimeRepository, userRepository, paymentRepository, promotionRepository,
                bookingPromotionRepository, promotionService, bookingMapper, promotionMapper,
                emailService, pricingService, foodItemRepository, bookingFoodRepository,
                foodItemMapper, null);
    }

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            SeatHoldRepository seatHoldRepository,
            TicketRepository ticketRepository,
            SeatRepository seatRepository,
            ShowtimeRepository showtimeRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            PromotionRepository promotionRepository,
            BookingPromotionRepository bookingPromotionRepository,
            PromotionService promotionService,
            BookingMapper bookingMapper,
            PromotionMapper promotionMapper,
            EmailService emailService,
            com.cinebook.service.PricingService pricingService
    ) {
        this(bookingRepository, seatHoldRepository, ticketRepository, seatRepository,
                showtimeRepository, userRepository, paymentRepository, promotionRepository,
                bookingPromotionRepository, promotionService, bookingMapper, promotionMapper,
                emailService, pricingService, null, null, null);
    }

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            SeatHoldRepository seatHoldRepository,
            TicketRepository ticketRepository,
            SeatRepository seatRepository,
            ShowtimeRepository showtimeRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            PromotionRepository promotionRepository,
            BookingPromotionRepository bookingPromotionRepository,
            PromotionService promotionService,
            BookingMapper bookingMapper,
            PromotionMapper promotionMapper,
            EmailService emailService
    ) {
        this(bookingRepository, seatHoldRepository, ticketRepository, seatRepository,
                showtimeRepository, userRepository, paymentRepository, promotionRepository,
                bookingPromotionRepository, promotionService, bookingMapper, promotionMapper,
                emailService, null, null, null, null);
    }

    private static com.cinebook.service.PricingService createFallbackPricingService() {
        return new com.cinebook.service.PricingService() {
            @Override
            public com.cinebook.dto.response.TicketPricingBreakdown calculateShowtimeBaseBreakdown(Showtime showtime) {
                BigDecimal base = (showtime != null && showtime.getBasePrice() != null) ? showtime.getBasePrice() : BigDecimal.ZERO;
                return com.cinebook.dto.response.TicketPricingBreakdown.builder()
                        .basePrice(base)
                        .seatTypeModifier(BigDecimal.ZERO)
                        .dayModifier(BigDecimal.ZERO)
                        .timeSlotModifier(BigDecimal.ZERO)
                        .finalPrice(base)
                        .build();
            }

            @Override
            public com.cinebook.dto.response.TicketPricingBreakdown calculateTicketPrice(Showtime showtime, com.cinebook.entity.SeatType seatType) {
                return calculateTicketPrice(calculateShowtimeBaseBreakdown(showtime), seatType);
            }

            @Override
            public com.cinebook.dto.response.TicketPricingBreakdown calculateTicketPrice(com.cinebook.dto.response.TicketPricingBreakdown baseBreakdown, com.cinebook.entity.SeatType seatType) {
                BigDecimal base = (baseBreakdown != null && baseBreakdown.getBasePrice() != null) ? baseBreakdown.getBasePrice() : BigDecimal.ZERO;
                BigDecimal dayMod = (baseBreakdown != null && baseBreakdown.getDayModifier() != null) ? baseBreakdown.getDayModifier() : BigDecimal.ZERO;
                BigDecimal timeMod = (baseBreakdown != null && baseBreakdown.getTimeSlotModifier() != null) ? baseBreakdown.getTimeSlotModifier() : BigDecimal.ZERO;
                BigDecimal seatMod = (seatType != null && seatType.getPriceModifier() != null) ? seatType.getPriceModifier() : BigDecimal.ZERO;
                BigDecimal finalPrice = base.add(seatMod).add(dayMod).add(timeMod).max(BigDecimal.ZERO);
                return com.cinebook.dto.response.TicketPricingBreakdown.builder()
                        .basePrice(base)
                        .seatTypeModifier(seatMod)
                        .dayModifier(dayMod)
                        .timeSlotModifier(timeMod)
                        .finalPrice(finalPrice)
                        .build();
            }

            @Override
            public com.cinebook.dto.response.ShowtimePricingPreviewResponse previewShowtimePricing(String showtimeId) {
                return null;
            }

            @Override
            public BigDecimal calculateMinimumTicketPrice(Showtime showtime) {
                return calculateShowtimeBaseBreakdown(showtime).getFinalPrice();
            }

            @Override
            public List<com.cinebook.dto.response.DayPricingRuleResponse> getAllDayPricingRules() { return List.of(); }
            @Override
            public com.cinebook.dto.response.DayPricingRuleResponse getDayPricingRuleById(String id) { return null; }
            @Override
            public com.cinebook.dto.response.DayPricingRuleResponse updateDayPricingRule(String id, com.cinebook.dto.request.UpdateDayPricingRuleRequest request) { return null; }
            @Override
            public com.cinebook.dto.response.DayPricingRuleResponse updateDayPricingRuleByDay(java.time.DayOfWeek dayOfWeek, BigDecimal modifier) { return null; }
            @Override
            public void initDefaultDayPricingRulesIfEmpty() {}
            @Override
            public List<com.cinebook.dto.response.TimeSlotPricingRuleResponse> getAllTimeSlotPricingRules() { return List.of(); }
            @Override
            public com.cinebook.dto.response.TimeSlotPricingRuleResponse getTimeSlotPricingRuleById(String id) { return null; }
            @Override
            public com.cinebook.dto.response.TimeSlotPricingRuleResponse createTimeSlotPricingRule(com.cinebook.dto.request.CreateTimeSlotPricingRuleRequest request) { return null; }
            @Override
            public com.cinebook.dto.response.TimeSlotPricingRuleResponse updateTimeSlotPricingRule(String id, com.cinebook.dto.request.UpdateTimeSlotPricingRuleRequest request) { return null; }
            @Override
            public void deleteTimeSlotPricingRule(String id) {}
        };
    }


    /**
     * Tạo đơn đặt vé mới và kích hoạt cơ chế giữ chỗ tạm thời (Seat Hold) trong 5 phút.
     * 
     * Quy trình xác thực và xử lý:
     * 1. Xác thực người dùng hiện tại từ SecurityContextHolder.
     * 2. Kiểm tra số lượng ghế: Không được để trống, không vượt quá 8 ghế, không trùng lặp.
     * 3. Kiểm tra tính hợp lệ của suất chiếu:
     *    - Trạng thái phải là SCHEDULED (không bị hủy hoặc đã kết thúc).
     *    - Thời gian bắt đầu chưa qua so với hiện tại.
     *    - Phòng chiếu (Auditorium) và rạp (Cinema) phải đang ở trạng thái ACTIVE.
     * 4. Kiểm tra ghế: Thuộc đúng phòng chiếu và đang ở trạng thái ACTIVE (không hỏng - BROKEN).
     * 5. Kiểm tra Idempotency: Nếu chính khách hàng này đã có đơn đặt vé PENDING_PAYMENT cho đúng
     *    tập ghế này trong cùng suất chiếu, trả về luôn đơn hiện tại mà không tạo mới trùng lặp.
     * 6. Kiểm tra xung đột ghế:
     *    - Xóa các giữ chỗ đã hết hạn trước đó.
     *    - Kiểm tra xem có ai khác đang giữ chỗ các ghế này không (SeatHold).
     *    - Kiểm tra xem ghế đã được bán chưa (Ticket có trạng thái VALID hoặc USED).
     * 7. Tính toán giá vé thông qua PricingService (bao gồm giá cơ bản, phụ thu ngày, giờ chiếu, loại ghế).
     * 8. Áp dụng mã khuyến mãi (nếu có): Kiểm tra thời hạn, giá trị tối thiểu, giới hạn lượt dùng,
     *    khóa bi quan để tăng usedCount một cách an toàn.
     * 9. Lưu Booking (trạng thái PENDING_PAYMENT) và tạo các bản ghi SeatHold (hết hạn sau 5 phút).
     */
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

        if (showtime.getEndTime() != null && (now.isAfter(showtime.getEndTime()) || now.isEqual(showtime.getEndTime()))) {
            throw new BadRequestException("Lịch chiếu đã kết thúc.");
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

        // Kiểm tra xem khách hàng này đã có đơn PENDING_PAYMENT cho đúng tập ghế này trong suất chiếu chưa (Idempotency)
        List<Booking> activeUserBookings = bookingRepository.findActiveBookingsByUserAndShowtime(user.getId(), showtime.getId(), now);
        for (Booking activeB : activeUserBookings) {
            List<SeatHold> userHolds = seatHoldRepository.findByBookingId(activeB.getId());
            Set<String> userHeldSeatIds = userHolds.stream().map(h -> h.getSeat().getId()).collect(Collectors.toSet());
            if (userHeldSeatIds.equals(uniqueSeatIds)) {
                log.info("Người dùng {} đang yêu cầu đặt lại đơn giữ chỗ hiện có {}. Trả về đơn giữ chỗ sẵn có.", user.getId(), activeB.getId());
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

        // Kiểm tra quy tắc không để lại ghế trống đơn lẻ (No Single Orphan Seat Rule)
        validateSeatAdjacency(showtime, seats);

        // Bước 1: Xóa chủ động các bản ghi SeatHold đã hết hạn cho các ghế được yêu cầu để tránh kích hoạt ràng buộc duy nhất uk_seat_holds_showtime_seat
        seatHoldRepository.deleteExpiredHoldsForSeats(showtime.getId(), uniqueSeatIds, now);

        List<SeatHold> activeHolds = seatHoldRepository.findActiveHoldsByShowtimeAndSeatIds(showtime.getId(), uniqueSeatIds, now);
        if (!activeHolds.isEmpty()) {
            throw new ConflictException("Một hoặc nhiều ghế đã được giữ chỗ bởi người khác. Vui lòng chọn ghế khác.");
        }

        // Bước 2: Kiểm tra các vé đã bán thực tế (cả trạng thái VALID và USED) để chống bán trùng ghế
        List<Ticket> soldTickets = ticketRepository.findTicketsByShowtimeAndSeatIdsAndStatuses(showtime.getId(), uniqueSeatIds, SOLD_TICKET_STATUSES);
        if (!soldTickets.isEmpty()) {
            throw new ConflictException("Một hoặc nhiều ghế đã được bán. Vui lòng chọn ghế khác.");
        }

        TicketPricingBreakdown baseBreakdown = pricingService.calculateShowtimeBaseBreakdown(showtime);
        BigDecimal grossAmount = BigDecimal.ZERO;
        List<BookingSeatResponse> seatResponses = new ArrayList<>();

        for (Seat seat : seats) {
            TicketPricingBreakdown pricing = pricingService.calculateTicketPrice(baseBreakdown, seat.getSeatType());
            BigDecimal price = pricing.getFinalPrice();
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

        // Xử lý và tính toán tiền bắp nước F&B (Server-side authoritative calculation)
        BigDecimal foodTotal = BigDecimal.ZERO;
        List<BookingFood> bookingFoodsToSave = new ArrayList<>();
        List<BookingFoodResponse> foodResponses = new ArrayList<>();

        if (request.getFoodItems() != null && !request.getFoodItems().isEmpty()) {
            Set<String> seenFoodIds = new HashSet<>();
            for (BookingFoodItemRequest foodReq : request.getFoodItems()) {
                if (foodReq.getFoodItemId() == null || !seenFoodIds.add(foodReq.getFoodItemId())) {
                    throw new BadRequestException("Danh sách bắp nước không được chứa món trùng lặp.");
                }

                if (foodReq.getQuantity() == null || foodReq.getQuantity() < 1) {
                    throw new BadRequestException("Số lượng món ăn/thức uống phải lớn hơn hoặc bằng 1.");
                }

                if (foodReq.getQuantity() > 20) {
                    throw new BadRequestException("Số lượng cho mỗi món ăn/thức uống không được vượt quá 20.");
                }

                if (foodItemRepository != null) {
                    FoodItem foodItem = foodItemRepository.findByIdAndDeletedAtIsNull(foodReq.getFoodItemId())
                            .orElseThrow(() -> new BadRequestException("Món ăn/thức uống không tồn tại: " + foodReq.getFoodItemId()));

                    if (foodItem.getStatus() != FoodItemStatus.ACTIVE) {
                        throw new BadRequestException("Món '" + foodItem.getName() + "' hiện đang tạm ngưng phục vụ.");
                    }

                    BigDecimal unitPrice = foodItem.getPrice();
                    BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(foodReq.getQuantity()));
                    foodTotal = foodTotal.add(subtotal);

                    BookingFood bf = new BookingFood();
                    bf.setFoodItem(foodItem);
                    bf.setFoodName(foodItem.getName());
                    bf.setUnitPrice(unitPrice);
                    bf.setQuantity(foodReq.getQuantity());
                    bf.setSubtotal(subtotal);
                    bookingFoodsToSave.add(bf);

                    foodResponses.add(BookingFoodResponse.builder()
                            .foodItemId(foodItem.getId())
                            .foodName(foodItem.getName())
                            .unitPrice(unitPrice)
                            .quantity(foodReq.getQuantity())
                            .subtotal(subtotal)
                            .build());
                }
            }
        }

        BigDecimal netTotal = grossAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        BigDecimal finalTotal = netTotal.add(foodTotal);
        LocalDateTime holdExpiresAt = now.plusMinutes(HOLD_DURATION_MINUTES);
        String bookingCode = generateUniqueBookingCode(now);

        Booking booking = new Booking();
        booking.setBookingCode(bookingCode);
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setTotalAmount(finalTotal);
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

            if (!bookingFoodsToSave.isEmpty() && bookingFoodRepository != null) {
                for (BookingFood bf : bookingFoodsToSave) {
                    bf.setBooking(savedBooking);
                }
                List<BookingFood> savedBfs = bookingFoodRepository.saveAllAndFlush(bookingFoodsToSave);
                savedBooking.setBookingFoods(savedBfs);
                if (foodItemMapper != null) {
                    foodResponses = foodItemMapper.toBookingFoodResponseList(savedBfs);
                }
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

        return bookingMapper.toBookingDetailResponse(savedBooking, seatResponses, Collections.emptyList(), Collections.emptyList(), promoResponse, foodResponses);
    }

    /**
     * Hủy đơn đặt vé khi hết hạn giữ chỗ (Lazy Expiration):
     * 
     * Quy trình xử lý:
     * 1. Áp dụng Khóa bi quan (Pessimistic Write Lock: findByIdWithLock) trên Booking để chống Race Condition.
     * 2. Kiểm tra nếu holdExpiresAt <= now:
     *    - Chuyển trạng thái Booking sang EXPIRED.
     *    - Lưu vết (Snapshot) các ghế đã giữ dưới dạng bản ghi Ticket với trạng thái CANCELLED trước khi xóa holds.
     *    - Xóa toàn bộ SeatHold của đơn để giải phóng ghế cho khách khác chọn.
     *    - Hoàn trả lại số lượt sử dụng mã khuyến mãi (Promotion quota) nếu đơn có áp dụng mã.
     *    - Hủy các yêu cầu thanh toán (Payment) đang ở trạng thái PENDING.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Booking expireBookingIfHoldExpired(Booking booking) {
        if (booking == null) {
            return null;
        }
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            return booking;
        }

        // 1. Áp dụng Khóa bi quan (Pessimistic Write Lock) trên Booking để chống tranh chấp đồng thời (Race Condition)
        Booking targetBooking = booking;
        if (booking.getId() != null) {
            targetBooking = bookingRepository.findByIdWithLock(booking.getId()).orElse(booking);
        }

        // 2. Tái kiểm tra trạng thái sau khi đã có lock để bảo đảm tính Idempotency
        if (targetBooking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            return targetBooking;
        }

        LocalDateTime now = LocalDateTime.now();
        if (targetBooking.getHoldExpiresAt() != null && !targetBooking.getHoldExpiresAt().isAfter(now)) {
            targetBooking.setBookingStatus(BookingStatus.EXPIRED);
            Booking saved = bookingRepository.save(targetBooking);
            Booking updatedBooking = (saved != null) ? saved : targetBooking;

            // 3. Lưu vết Snapshot các ghế đã giữ dưới dạng vé CANCELLED trước khi giải phóng ghế
            snapshotHeldSeatsAsCancelledTickets(updatedBooking);

            seatHoldRepository.deleteByBookingId(targetBooking.getId());
            releasePromotionQuotaIfApplied(targetBooking.getId());

            // 4. Hủy các yêu cầu thanh toán PENDING đang dở dang (giữ nguyên trạng thái các giao dịch đã xong)
            cancelPendingPaymentsForBooking(targetBooking.getId());

            log.info("Hủy thành công đơn giữ chỗ hết hạn: code={}, id={}", updatedBooking.getBookingCode(), updatedBooking.getId());
            return updatedBooking;
        }
        return targetBooking;
    }

    /**
     * Hủy toàn bộ các phiên thanh toán PENDING đang dở dang liên quan đến đơn hàng này.
     * 
     * @param bookingId Mã định danh đơn hàng
     */
    private void cancelPendingPaymentsForBooking(String bookingId) {
        List<Payment> pendingPayments = paymentRepository.findByBookingId(bookingId).stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PENDING)
                .toList();
        for (Payment p : pendingPayments) {
            p.setPaymentStatus(PaymentStatus.CANCELLED);
        }
        if (!pendingPayments.isEmpty()) {
            paymentRepository.saveAll(pendingPayments);
            log.info("Đã hủy {} phiên thanh toán PENDING cho đơn hàng {}", pendingPayments.size(), bookingId);
        }
    }

    /**
     * Hoàn trả số lượt sử dụng mã khuyến mãi (Promotion Quota) khi đơn hàng bị hủy hoặc hết hạn.
     * Sử dụng khóa bi quan (Pessimistic Lock) trên Promotion để tránh Race Condition khi cập nhật usedCount.
     * 
     * @param bookingId Mã định danh đơn hàng
     */
    private void releasePromotionQuotaIfApplied(String bookingId) {
        List<BookingPromotion> bookingPromotions = bookingPromotionRepository.findByBookingId(bookingId);
        for (BookingPromotion bp : bookingPromotions) {
            Promotion promo = promotionRepository.findByIdWithLock(bp.getPromotion().getId()).orElse(null);
            if (promo != null && promo.getUsedCount() > 0) {
                promo.setUsedCount(promo.getUsedCount() - 1);
                promotionRepository.save(promo);
                log.info("Đã hoàn trả 1 lượt dùng mã khuyến mãi {}: lượt dùng mới = {}", promo.getCode(), promo.getUsedCount());
            }
        }
    }

    /**
     * Lưu vết lịch sử các ghế khách đã chọn (Snapshotting) dưới dạng vé CANCELLED trước khi xóa seat_holds.
     * Nghiệp vụ này đảm bảo khi kiểm toán hoặc khách hỏi lại lịch sử, hệ thống vẫn biết khách từng chọn ghế nào.
     * 
     * @param booking Đơn đặt vé bị hủy hoặc hết hạn
     */
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

        TicketPricingBreakdown baseBreakdown = pricingService.calculateShowtimeBaseBreakdown(booking.getShowtime());
        List<Ticket> cancelledTickets = new ArrayList<>();
        for (SeatHold hold : holds) {
            Seat seat = hold.getSeat();
            SeatType seatType = (seat != null) ? seat.getSeatType() : null;
            TicketPricingBreakdown pricing = pricingService.calculateTicketPrice(baseBreakdown, seatType);
            BigDecimal ticketPrice = pricing.getFinalPrice();

            String ticketId = UUID.randomUUID().toString();
            Ticket ticket = new Ticket();
            ticket.setId(ticketId);
            ticket.setBooking(booking);
            ticket.setSeat(seat);
            ticket.setTicketPrice(ticketPrice);
            ticket.setTicketStatus(TicketStatus.CANCELLED);
            ticket.setQrCode(ticketId);
            cancelledTickets.add(ticket);
        }

        try {
            ticketRepository.saveAllAndFlush(cancelledTickets);
            log.info("Đã lưu {} bản ghi vé snapshot CANCELLED cho đơn hàng {}", cancelledTickets.size(), booking.getBookingCode());
        } catch (Exception ex) {
            log.warn("Không thể lưu bản ghi vé snapshot CANCELLED cho đơn hàng {}: {}", booking.getBookingCode(), ex.getMessage());
        }
    }

    /**
     * Dọn dẹp trực tiếp các bản ghi giữ chỗ (SeatHold) mồ côi đã hết hạn trong cơ sở dữ liệu.
     * 
     * @param now Thời điểm hiện tại
     * @return Số lượng bản ghi giữ chỗ đã xóa
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredSeatHolds(LocalDateTime now) {
        return seatHoldRepository.deleteExpiredHolds(now);
    }

    /**
     * Lấy thông tin chi tiết của một đơn đặt vé (Booking Detail):
     * - Kiểm tra quyền sở hữu: Khách hàng chỉ được xem đơn của mình; ADMIN có quyền xem mọi đơn.
     * - Lazy Expiration: Nếu đơn đang là PENDING_PAYMENT mà đã quá 5 phút, tự động hết hạn và giải phóng ghế ngay lập tức.
     * - Trả về đầy đủ thông tin: Ghế, vé điện tử (nếu có), lịch sử thanh toán, chi tiết khuyến mãi.
     * 
     * @param bookingId Mã định danh đơn hàng
     * @return BookingDetailResponse chi tiết đơn hàng
     */
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

        List<BookingFoodResponse> foodResponses = (bookingFoodRepository != null && foodItemMapper != null)
                ? foodItemMapper.toBookingFoodResponseList(bookingFoodRepository.findByBookingId(booking.getId()))
                : Collections.emptyList();

        return bookingMapper.toBookingDetailResponse(booking, seatResponses, ticketResponses, paymentResponses, promoResponse, foodResponses);
    }

    /**
     * Lấy danh sách lịch sử đặt vé có phân trang của người dùng hiện tại đang đăng nhập.
     * 
     * @param status Trạng thái đơn đặt vé cần lọc (PENDING_PAYMENT, PAID, CANCELLED, EXPIRED, REFUNDED) - tùy chọn
     * @param pageable Tham số phân trang và sắp xếp
     * @return Trang danh sách tóm tắt các đơn đặt vé (PageResponse<BookingSummaryResponse>)
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> getMyBookings(BookingStatus status, Pageable pageable) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        Page<Booking> page = (status != null)
                ? bookingRepository.findByUserIdAndBookingStatus(currentUserId, status, pageable)
                : bookingRepository.findByUserId(currentUserId, pageable);

        return PageResponse.of(page, bookingMapper::toBookingSummaryResponse);
    }

    /**
     * Tra cứu và lọc danh sách toàn bộ đơn đặt vé trong hệ thống dành cho Quản trị viên (Admin).
     * 
     * @param q Từ khóa tìm kiếm (mã đơn bookingCode, email khách hàng, họ tên, số điện thoại)
     * @param status Trạng thái đơn hàng cần lọc
     * @param showtimeId Lọc theo suất chiếu cụ thể
     * @param pageable Tham số phân trang và sắp xếp
     * @return Trang danh sách đơn đặt vé phù hợp
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> getAdminBookings(String q, BookingStatus status, String showtimeId, Pageable pageable) {
        String keyword = StringUtils.hasText(q) ? q.trim() : null;
        String stId = StringUtils.hasText(showtimeId) ? showtimeId.trim() : null;
        Page<Booking> page = bookingRepository.findAdminBookings(keyword, status, stId, pageable);
        return PageResponse.of(page, bookingMapper::toBookingSummaryResponse);
    }

    /**
     * Khách hàng hoặc Admin chủ động hủy đơn đặt vé đang ở trạng thái PENDING_PAYMENT (chưa thanh toán).
     * 
     * Quy trình xử lý:
     * 1. Khóa bi quan (Pessimistic Write Lock: findByIdWithLock) trên Booking để tránh Race Condition.
     * 2. Kiểm tra quyền sở hữu (hoặc quyền Admin).
     * 3. Kiểm tra tính hợp lệ: Chỉ được hủy đơn PENDING_PAYMENT; không được tự hủy đơn đã PAID (phải qua hoàn tiền).
     * 4. Kiểm tra thời hạn giữ chỗ: Nếu đã quá 5 phút, chuyển sang xử lý Lazy Expiration.
     * 5. Cập nhật trạng thái Booking sang CANCELLED, ghi nhận người hủy và lý do hủy.
     * 6. Lưu vết Snapshot các ghế đã giữ dưới dạng vé CANCELLED trước khi xóa holds.
     * 7. Xóa các bản ghi SeatHold để giải phóng ghế ngay lập tức cho khách khác.
     * 8. Hoàn trả lại số lượt dùng mã khuyến mãi (Promotion Quota) nếu có áp dụng.
     * 9. Hủy các yêu cầu thanh toán (Payment) PENDING liên quan.
     */
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

        // Lưu vết lịch sử các ghế khách đã giữ dưới dạng vé CANCELLED trước khi giải phóng ghế
        snapshotHeldSeatsAsCancelledTickets(updatedBooking);

        seatHoldRepository.deleteByBookingId(booking.getId());

        // Hoàn trả lại số lượt dùng khuyến mãi nếu đơn hàng có áp dụng mã
        releasePromotionQuotaIfApplied(booking.getId());

        // Hủy các phiên thanh toán PENDING đang dở dang
        cancelPendingPaymentsForBooking(booking.getId());

        // Tạo thông báo hủy đơn đặt vé thành công (In-App Notification)
        try {
            if (notificationService != null && updatedBooking.getUser() != null) {
                notificationService.createNotification(
                        updatedBooking.getUser(),
                        updatedBooking,
                        NotificationType.BOOKING_CANCELLED,
                        "Hủy đơn đặt vé thành công",
                        "Đơn đặt vé #" + updatedBooking.getBookingCode() + " đã được hủy thành công."
                );
            }
        } catch (Exception e) {
            log.error("Không thể tạo thông báo hủy đơn đặt vé {}: {}", updatedBooking.getBookingCode(), e.getMessage());
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

    /**
     * Xác nhận thanh toán thành công và phát hành vé điện tử chính thức (Ticket Issuance).
     * 
     * Quy trình xử lý:
     * 1. Đối chiếu tính toàn vẹn: Payment thuộc đúng Booking, trạng thái Payment là SUCCESS,
     *    số tiền thanh toán khớp 100% với booking.totalAmount.
     * 2. Idempotency Check: Nếu đơn hàng đã ở trạng thái PAID (ví dụ cả IPN và Return URL cùng kích hoạt),
     *    trả về ngay thông tin hiện tại, tuyệt đối không tạo vé trùng lặp (không double-ticket).
     * 3. Chuyển trạng thái Booking sang PAID.
     * 4. Phát hành vé điện tử (Ticket):
     *    - Mỗi ghế giữ chỗ (SeatHold) sẽ được chuyển thành một bản ghi Ticket với trạng thái VALID.
     *    - Ghi nhận bất biến (Snapshot) giá vé tại thời điểm bán (ticket_price).
     *    - Tạo mã QR Code định danh duy nhất (UUID) phục vụ quy trình soát vé tại rạp.
     * 5. Xóa bỏ các bản ghi SeatHold (chuyển giao hoàn toàn quyền sở hữu ghế cho Ticket).
     * 6. Gửi email xác nhận kèm vé điện tử cho khách hàng.
     */
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

        // 2. Kiểm tra tính Idempotent: Nếu đơn hàng đã ở trạng thái PAID, trả về thông tin hiện tại, tuyệt đối không tạo vé trùng lặp
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

        TicketPricingBreakdown baseBreakdown = pricingService.calculateShowtimeBaseBreakdown(booking.getShowtime());
        List<Ticket> createdTickets = new ArrayList<>();

        for (SeatHold hold : holds) {
            Seat seat = hold.getSeat();
            SeatType seatType = (seat != null) ? seat.getSeatType() : null;
            TicketPricingBreakdown pricing = pricingService.calculateTicketPrice(baseBreakdown, seatType);
            BigDecimal ticketPrice = pricing.getFinalPrice();

            String ticketId = UUID.randomUUID().toString();
            Ticket ticket = new Ticket();
            ticket.setId(ticketId);
            ticket.setBooking(updatedBooking);
            ticket.setSeat(seat);
            ticket.setTicketPrice(ticketPrice);
            ticket.setTicketStatus(TicketStatus.VALID);
            ticket.setQrCode(ticketId); // Sử dụng UUID làm mã QR soát vé tại quầy
            createdTickets.add(ticket);
        }

        try {
            ticketRepository.saveAllAndFlush(createdTickets);
        } catch (DataIntegrityViolationException ex) {
            log.error("Duplicate ticket creation attempt for booking {}: {}", booking.getId(), ex.getMessage());
            throw new ConflictException("Vé cho ghế này đã tồn tại.");
        }

        seatHoldRepository.deleteByBookingId(booking.getId());

        // 6. Gửi email xác nhận kèm vé điện tử cho khách hàng một cách an toàn (bắt ngoại lệ để không làm rollback giao dịch)
        try {
            String customerEmail = updatedBooking.getUser() != null ? updatedBooking.getUser().getEmail() : null;
            String customerName = updatedBooking.getUser() != null ? updatedBooking.getUser().getFullName() : null;
            if (customerEmail != null) {
                emailService.sendBookingConfirmationEmail(customerEmail, customerName, updatedBooking, createdTickets);
            }
        } catch (Exception e) {
            log.error("Lỗi khi gửi email xác nhận đặt vé {}: {}", updatedBooking.getBookingCode(), e.getMessage());
        }

        // 7. Tạo thông báo thanh toán thành công (In-App Notification)
        try {
            if (notificationService != null && updatedBooking.getUser() != null) {
                notificationService.createNotification(
                        updatedBooking.getUser(),
                        updatedBooking,
                        NotificationType.PAYMENT_SUCCESS,
                        "Thanh toán thành công",
                        "Đơn đặt vé #" + updatedBooking.getBookingCode() + " đã được thanh toán thành công. Chúc bạn xem phim vui vẻ!"
                );
            }
        } catch (Exception e) {
            log.error("Không thể tạo thông báo thanh toán thành công cho đơn vé {}: {}", updatedBooking.getBookingCode(), e.getMessage());
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

    /**
     * Xử lý cập nhật trạng thái hoàn tiền cho đơn đặt vé sau khi cổng thanh toán (VNPay) đã chấp thuận yêu cầu:
     * 
     * Quy trình xử lý:
     * 1. Khóa bi quan (Pessimistic Write Lock: findByIdWithLock) trên Booking để tránh Race Condition.
     * 2. Idempotency Check: Nếu đơn hàng đã là REFUNDED, trả về ngay thông tin hiện tại.
     * 3. Kiểm tra tính hợp lệ: Chỉ hoàn tiền cho đơn đã PAID (hoặc EXPIRED do Admin xử lý đối soát tài chính).
     * 4. Kiểm tra vé: Tuyệt đối không hoàn tiền nếu đã có bất kỳ vé nào có trạng thái USED (khách đã vào xem phim).
     * 5. Chuyển trạng thái Booking sang REFUNDED, lưu mốc thời gian và lý do hoàn tiền.
     * 6. Hủy toàn bộ vé điện tử: Chuyển tất cả Ticket sang trạng thái CANCELLED.
     * 7. Xóa sạch mọi bản ghi giữ chỗ SeatHold liên quan.
     * 
     * @param bookingId Mã định danh đơn hàng
     * @param reason Lý do hoàn tiền
     * @param userId Người thực hiện thao tác hoàn tiền
     * @return BookingDetailResponse chi tiết đơn sau khi hoàn tiền
     */
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

        // Tạo thông báo hoàn tiền thành công (In-App Notification)
        try {
            if (notificationService != null && updatedBooking.getUser() != null) {
                notificationService.createNotification(
                        updatedBooking.getUser(),
                        updatedBooking,
                        NotificationType.REFUND_COMPLETED,
                        "Hoàn tiền thành công",
                        "Đơn đặt vé #" + updatedBooking.getBookingCode() + " đã được hoàn tiền thành công."
                );
            }
        } catch (Exception e) {
            log.error("Không thể tạo thông báo hoàn tiền thành công cho đơn vé {}: {}", updatedBooking.getBookingCode(), e.getMessage());
        }

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



    /**
     * Tra cứu trạng thái khả dụng và giá vé tính toán của toàn bộ ghế ngồi trong một suất chiếu:
     * 
     * Quy tắc xác định trạng thái ghế (SeatAvailabilityStatus):
     * 1. BLOCKED: Phòng chiếu không ACTIVE, hoặc lịch chiếu bị CANCELLED, hoặc ghế bị sự cố vật lý (SeatStatus != ACTIVE).
     * 2. SOLD: Ghế đã được bán thành công (tồn tại vé với trạng thái VALID hoặc USED).
     * 3. HELD: Ghế đang được giữ chỗ trong 5 phút bởi một giao dịch PENDING_PAYMENT chưa hết hạn.
     *    - Đánh dấu cờ `isHeldByCurrentUser = true` nếu chính người dùng đang đăng nhập là người giữ ghế này.
     * 4. AVAILABLE: Ghế hoàn toàn trống, sẵn sàng để khách hàng chọn đặt.
     * 
     * Tính toán giá vé thời gian thực (Dynamic Pricing):
     * - Kết hợp giá cơ sở của suất chiếu (basePrice) với phụ thu ngày chiếu (Day Modifier),
     *   phụ thu khung giờ (TimeSlot Modifier) và phụ thu loại ghế (SeatType Modifier: VIP, Thường, Đôi).
     * 
     * @param showtimeId Mã định danh suất chiếu
     * @return Danh sách ShowtimeSeatStatusResponse phản ánh sơ đồ ghế và giá tương ứng
     */
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

        TicketPricingBreakdown baseBreakdown = pricingService.calculateShowtimeBaseBreakdown(showtime);
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
            TicketPricingBreakdown pricing = pricingService.calculateTicketPrice(baseBreakdown, seatType);
            responses.add(ShowtimeSeatStatusResponse.builder()
                    .id(seat.getId())
                    .auditoriumId(auditorium.getId())
                    .seatTypeId(seatType != null ? seatType.getId() : null)
                    .seatTypeName(seatType != null ? seatType.getName() : null)
                    .seatTypeCode(seatType != null ? seatType.getCode() : null)
                    .capacity(seatType != null ? seatType.getCapacity() : (short) 1)
                    .colorToken(seatType != null ? seatType.getColorToken() : null)
                    .icon(seatType != null ? seatType.getIcon() : null)
                    .priceModifier(pricing.getSeatTypeModifier())
                    .calculatedPrice(pricing.getFinalPrice())
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

    /**
     * Tra cứu đơn đặt vé đang chờ thanh toán (PENDING_PAYMENT) và còn hiệu lực giữ chỗ của khách hàng cho một suất chiếu.
     * Hỗ trợ khôi phục phiên đặt vé (Resume Booking) khi khách tải lại trang sơ đồ ghế.
     * 
     * @param showtimeId Mã định danh suất chiếu
     * @return BookingDetailResponse nếu có đơn đang giữ chỗ hợp lệ, ngược lại trả về null
     */
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

    /**
     * Xác thực quyền truy cập đơn đặt vé:
     * - Quản trị viên (ROLE_ADMIN) có quyền truy cập toàn bộ đơn vé.
     * - Khách hàng chỉ có quyền xem hoặc hủy đơn hàng do chính mình tạo ra.
     * 
     * @param booking Đơn đặt vé cần kiểm tra
     * @throws ForbiddenException nếu người dùng không có quyền truy cập
     */
    private void validateBookingOwnershipOrAdmin(Booking booking) {
        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        boolean isAdmin = currentUser.isAdmin();

        if (!isAdmin && !booking.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Bạn không có quyền truy cập đơn đặt vé này.");
        }
    }

    /**
     * Chuyển đổi danh sách ghế thành đối tượng phản hồi BookingSeatResponse:
     * - Nếu đơn đã phát hành vé (Ticket): Lấy thông tin ghế và giá vé từ bảng Ticket.
     * - Nếu đơn đang trong giai đoạn giữ chỗ (SeatHold): Tính toán giá vé tương ứng theo SeatType và Showtime Base Breakdown.
     * 
     * @param booking Đơn đặt vé cần trích xuất danh sách ghế
     * @return Danh sách BookingSeatResponse
     */
    private List<BookingSeatResponse> buildBookingSeatResponses(Booking booking) {
        List<Ticket> tickets = ticketRepository.findByBookingId(booking.getId());
        if (!tickets.isEmpty()) {
            return tickets.stream()
                    .map(t -> bookingMapper.toBookingSeatResponse(t.getSeat(), t.getTicketPrice()))
                    .toList();
        }

        List<SeatHold> holds = seatHoldRepository.findByBookingId(booking.getId());
        if (!holds.isEmpty()) {
            TicketPricingBreakdown baseBreakdown = pricingService.calculateShowtimeBaseBreakdown(booking.getShowtime());
            return holds.stream()
                    .map(h -> {
                        Seat seat = h.getSeat();
                        SeatType seatType = (seat != null) ? seat.getSeatType() : null;
                        TicketPricingBreakdown pricing = pricingService.calculateTicketPrice(baseBreakdown, seatType);
                        return bookingMapper.toBookingSeatResponse(seat, pricing.getFinalPrice());
                    })
                    .toList();
        }

        return Collections.emptyList();
    }

    /**
     * Sinh mã đơn đặt vé duy nhất định dạng CB-yyyyMMdd-XXXXXX:
     * - Tiền tố CB- cùng ngày đặt vé yyyyMMdd giúp phân loại đơn hàng theo ngày.
     * - Hậu tố ngẫu nhiên 6 ký tự (chữ in hoa và số) với thuật toán SecureRandom.
     * - Kiểm tra tính duy nhất trong cơ sở dữ liệu (existsByBookingCode) để chống trùng mã.
     * 
     * @param now Thời điểm tạo đơn
     * @return Chuỗi mã đơn đặt vé duy nhất
     */
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

    /**
     * Tra cứu và xác minh tính hợp lệ của mã soát vé (Check-in Verification) trước khi cho khách vào phòng chiếu.
     * 
     * Quy tắc xác minh:
     * 1. Mã soát vé phải tồn tại trong hệ thống.
     * 2. Đơn vé không bị Hủy (CANCELLED), Hoàn tiền (REFUNDED) hoặc Hết hạn (EXPIRED).
     * 3. Đơn vé phải ở trạng thái đã thanh toán (PAID).
     * 4. Lịch chiếu không bị hủy (showtime.status != CANCELLED).
     * 5. Phải còn ít nhất một vé có trạng thái VALID chưa bị soát (USED).
     * 
     * @param checkInCode Mã soát vé định dạng CHECKIN-xxx
     * @return BookingVerifyResponse thông tin chi tiết đơn hàng, danh sách vé và cờ checkInEligible
     */
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
            SeatType st = s != null ? s.getSeatType() : null;
            return BookingTicketItemResponse.builder()
                    .ticketId(t.getId())
                    .seatCode(s != null ? s.getSeatCode() : null)
                    .rowLabel(s != null ? s.getRowLabel() : null)
                    .seatNumber(s != null && s.getSeatNumber() != null ? s.getSeatNumber().intValue() : null)
                    .seatTypeName(st != null ? st.getName() : null)
                    .seatTypeCode(st != null ? st.getCode() : null)
                    .capacity(st != null && st.getCapacity() != null ? st.getCapacity() : (short) 1)
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

    /**
     * Thực hiện thao tác soát vé nguyên tử (Atomic Check-in):
     * 
     * Cơ chế khóa & Chống Race Condition:
     * 1. Khóa bi quan hàng Booking trước (Pessimistic Lock: findByCheckInCodeWithLock) để bảo đảm
     *    thứ tự khóa nhất quán toàn cục (Global Lock Ordering: Booking -> Ticket), chống Deadlock.
     * 2. Khóa các bản ghi vé (findByBookingIdWithLock).
     * 3. Kiểm tra tính hợp lệ của đơn hàng và suất chiếu.
     * 4. Cập nhật đồng loạt các vé có trạng thái VALID sang USED.
     * 5. Ghi log kiểm toán và trả về phản hồi kết quả soát vé.
     * 
     * @param request Yêu cầu chứa mã soát vé checkInCode
     * @return BookingCheckInResponse kết quả soát vé chi tiết
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingCheckInResponse checkInBooking(BookingCheckInRequest request) {
        if (request == null || !StringUtils.hasText(request.getCheckInCode())) {
            throw new BadRequestException("Mã soát vé không được để trống.");
        }

        String code = request.getCheckInCode().trim();

        // 1. Khóa hàng Booking trước để bảo đảm thứ tự khóa toàn cục (Booking -> Ticket)
        Booking booking = bookingRepository.findByCheckInCodeWithLock(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với mã soát vé: " + code));

        // 2. Khóa các bản ghi vé của đơn hàng này
        List<Ticket> tickets = ticketRepository.findByBookingIdWithLock(booking.getId());

        // 3. Kiểm tra tính hợp lệ của trạng thái đơn hàng và suất chiếu
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

        log.info("Soát vé đơn hàng {} (mã: {}): {} vé chuyển sang USED, {} vé đã dùng trước đó",
                booking.getId(), booking.getBookingCode(), validTickets.size(), alreadyUsedCount);

        List<Ticket> reloadedTickets = ticketRepository.findByBookingIdWithSeat(booking.getId());
        List<BookingTicketItemResponse> ticketItems = reloadedTickets.stream().map(t -> {
            Seat s = t.getSeat();
            SeatType st = s != null ? s.getSeatType() : null;
            return BookingTicketItemResponse.builder()
                    .ticketId(t.getId())
                    .seatCode(s != null ? s.getSeatCode() : null)
                    .rowLabel(s != null ? s.getRowLabel() : null)
                    .seatNumber(s != null && s.getSeatNumber() != null ? s.getSeatNumber().intValue() : null)
                    .seatTypeName(st != null ? st.getName() : null)
                    .seatTypeCode(st != null ? st.getCode() : null)
                    .capacity(st != null && st.getCapacity() != null ? st.getCapacity() : (short) 1)
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

    /**
     * Kiểm tra quy tắc không để lại ghế trống đơn lẻ (No Single Orphan Seat Rule).
     * Server-side authoritative validation trước khi tạo SeatHold.
     */
    private void validateSeatAdjacency(Showtime showtime, List<Seat> requestedSeats) {
        if (requestedSeats == null || requestedSeats.isEmpty() || showtime == null) {
            return;
        }

        Auditorium auditorium = showtime.getAuditorium();
        if (auditorium == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<SeatHold> activeHolds = seatHoldRepository.findByShowtimeIdAndExpiresAtAfter(showtime.getId(), now);
        Set<String> heldSeatIds = activeHolds.stream()
                .map(h -> h.getSeat().getId())
                .collect(Collectors.toSet());

        List<Ticket> soldTickets = ticketRepository.findTicketsByShowtimeIdAndStatuses(showtime.getId(), SOLD_TICKET_STATUSES);
        Set<String> soldSeatIds = soldTickets.stream()
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toSet());

        Set<String> requestedSeatIds = requestedSeats.stream()
                .map(Seat::getId)
                .collect(Collectors.toSet());

        // Gom các ghế được yêu cầu theo hàng (rowLabel)
        Set<String> affectedRowLabels = requestedSeats.stream()
                .map(Seat::getRowLabel)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        for (String rowLabel : affectedRowLabels) {
            List<Seat> rowSeats = seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc(auditorium.getId(), rowLabel);
            if (rowSeats == null || rowSeats.isEmpty()) {
                continue;
            }

            // Phân chia hàng thành các khối ghế vật lý liên tục (bị ngăn cách bởi ghế BROKEN hoặc khoảng trống/lối đi)
            List<List<Seat>> blocks = new ArrayList<>();
            List<Seat> currentBlock = new ArrayList<>();

            for (Seat seat : rowSeats) {
                if (seat.getStatus() != SeatStatus.ACTIVE) {
                    if (!currentBlock.isEmpty()) {
                        blocks.add(currentBlock);
                        currentBlock = new ArrayList<>();
                    }
                    continue;
                }

                if (currentBlock.isEmpty()) {
                    currentBlock.add(seat);
                } else {
                    Seat prevSeat = currentBlock.get(currentBlock.size() - 1);
                    int prevCap = (prevSeat.getSeatType() != null && prevSeat.getSeatType().getCapacity() != null && prevSeat.getSeatType().getCapacity() > 0)
                            ? prevSeat.getSeatType().getCapacity()
                            : 1;

                    if (prevSeat.getSeatNumber() + prevCap == seat.getSeatNumber()) {
                        currentBlock.add(seat);
                    } else {
                        // Khoảng trống / lối đi giữa các ghế
                        blocks.add(currentBlock);
                        currentBlock = new ArrayList<>();
                        currentBlock.add(seat);
                    }
                }
            }
            if (!currentBlock.isEmpty()) {
                blocks.add(currentBlock);
            }

            // Đếm số ghế đơn lẻ trước và sau khi chọn
            int orphanBefore = 0;
            int orphanAfter = 0;

            for (List<Seat> block : blocks) {
                orphanBefore += countOrphanRunsInBlock(block, s -> !heldSeatIds.contains(s.getId()) && !soldSeatIds.contains(s.getId()));
                orphanAfter += countOrphanRunsInBlock(block, s -> !heldSeatIds.contains(s.getId()) && !soldSeatIds.contains(s.getId()) && !requestedSeatIds.contains(s.getId()));
            }

            if (orphanAfter > orphanBefore) {
                throw new BadRequestException("Không thể đặt vé: Lựa chọn ghế để lại ghế trống đơn lẻ (ghế cô lập) tại hàng " + rowLabel + ". Vui lòng chọn ghế liền kề.");
            }
        }
    }

    private int countOrphanRunsInBlock(List<Seat> block, java.util.function.Predicate<Seat> isAvailablePredicate) {
        int orphanCount = 0;
        int currentRunCapacity = 0;

        for (Seat seat : block) {
            int cap = (seat.getSeatType() != null && seat.getSeatType().getCapacity() != null && seat.getSeatType().getCapacity() > 0)
                    ? seat.getSeatType().getCapacity()
                    : 1;

            if (isAvailablePredicate.test(seat)) {
                currentRunCapacity += cap;
            } else {
                if (currentRunCapacity == 1) {
                    orphanCount++;
                }
                currentRunCapacity = 0;
            }
        }

        if (currentRunCapacity == 1) {
            orphanCount++;
        }

        return orphanCount;
    }
}
