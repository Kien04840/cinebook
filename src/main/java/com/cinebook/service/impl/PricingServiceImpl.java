package com.cinebook.service.impl;

import com.cinebook.dto.request.CreateTimeSlotPricingRuleRequest;
import com.cinebook.dto.request.UpdateDayPricingRuleRequest;
import com.cinebook.dto.request.UpdateTimeSlotPricingRuleRequest;
import com.cinebook.dto.response.DayPricingRuleResponse;
import com.cinebook.dto.response.ShowtimePricingPreviewResponse;
import com.cinebook.dto.response.TicketPricingBreakdown;
import com.cinebook.dto.response.TimeSlotPricingRuleResponse;
import com.cinebook.entity.DayPricingRule;
import com.cinebook.entity.SeatType;
import com.cinebook.entity.Showtime;
import com.cinebook.entity.TimeSlotPricingRule;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.repository.DayPricingRuleRepository;
import com.cinebook.repository.SeatTypeRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.repository.TimeSlotPricingRuleRepository;
import com.cinebook.service.PricingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Dịch vụ tính toán giá vé động và quản lý các quy tắc định giá (Dynamic Pricing Engine).
 * 
 * Công thức tính giá vé chuẩn của CineBook (Capacity-Aware Dynamic Pricing):
 *   Giá vé = (Giá gốc suất chiếu * Sức chứa loại ghế) + Phụ thu loại ghế + Phụ thu thứ trong tuần + Phụ thu khung giờ chiếu
 *   finalPrice = (basePrice * capacity) + seatTypeModifier + dayModifier + timeSlotModifier
 * 
 * Rationale thiết kế:
 * 1. Giá gốc (basePrice) nhân theo sức chứa (capacity): Ghế đơn (Standard, VIP) capacity=1 -> nhân 1.
 *    Ghế đôi (Couple) capacity=2 (dành cho 2 người ngồi) -> giá gốc tự động nhân 2.
 * 2. Phụ thu loại ghế (seatTypeModifier): Áp dụng cho từng đơn vị ghế (ví dụ: ghế VIP phụ thu 20.000 VND,
 *    ghế đôi phụ thu tiện ích không gian 40.000 VND).
 * 3. Phụ thu ngày (dayModifier): Tự động áp dụng theo thứ trong tuần (ví dụ: Thứ 7, CN tăng thêm 10.000 VND).
 * 4. Phụ thu khung giờ (timeSlotModifier): Tự động áp dụng theo mốc giờ chiếu (ví dụ: Khung giờ vàng 18:00 - 22:00 tăng thêm 10.000 VND).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final DayPricingRuleRepository dayPricingRuleRepository;
    private final TimeSlotPricingRuleRepository timeSlotPricingRuleRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatTypeRepository seatTypeRepository;

    @PostConstruct
    public void initOnStartup() {
        try {
            initDefaultDayPricingRulesIfEmpty();
        } catch (Exception ex) {
            log.warn("Failed to initialize default day pricing rules on startup: {}", ex.getMessage());
        }
    }

    /**
     * Tính toán cấu phần giá cơ bản của suất chiếu gồm: Giá gốc, Phụ thu ngày và Phụ thu khung giờ.
     */
    @Override
    @Transactional(readOnly = true)
    public TicketPricingBreakdown calculateShowtimeBaseBreakdown(Showtime showtime) {
        BigDecimal basePrice = (showtime != null && showtime.getBasePrice() != null)
                ? showtime.getBasePrice()
                : BigDecimal.ZERO;

        BigDecimal dayModifier = BigDecimal.ZERO;
        DayOfWeek dayOfWeek = null;
        BigDecimal timeSlotModifier = BigDecimal.ZERO;
        String timeSlotRange = null;

        if (showtime != null && showtime.getStartTime() != null) {
            dayOfWeek = showtime.getStartTime().getDayOfWeek();
            DayPricingRule dayRule = dayPricingRuleRepository.findByDayOfWeek(dayOfWeek).orElse(null);
            if (dayRule != null && dayRule.getModifier() != null) {
                dayModifier = dayRule.getModifier();
            }

            LocalTime time = showtime.getStartTime().toLocalTime();
            List<TimeSlotPricingRule> applicableSlotRules = timeSlotPricingRuleRepository.findApplicableRules(time);
            if (!applicableSlotRules.isEmpty()) {
                TimeSlotPricingRule matchedRule = applicableSlotRules.get(0);
                if (matchedRule.getModifier() != null) {
                    timeSlotModifier = matchedRule.getModifier();
                }
                timeSlotRange = matchedRule.getStartTime() + " - " + matchedRule.getEndTime();
            }
        }

        BigDecimal finalPrice = basePrice.add(dayModifier).add(timeSlotModifier).max(BigDecimal.ZERO);

        return TicketPricingBreakdown.builder()
                .basePrice(basePrice)
                .seatTypeModifier(BigDecimal.ZERO)
                .dayModifier(dayModifier)
                .dayOfWeek(dayOfWeek)
                .timeSlotModifier(timeSlotModifier)
                .timeSlotRange(timeSlotRange)
                .finalPrice(finalPrice)
                .build();
    }

    /**
     * Tính toán chi tiết cấu phần giá vé cho một ghế cụ thể thuộc suất chiếu.
     *
     * @param showtime Suất chiếu đang chọn
     * @param seatType Loại ghế (Standard, VIP, Couple)
     * @return Bảng phân tích cấu phần giá vé đầy đủ
     */
    @Override
    @Transactional(readOnly = true)
    public TicketPricingBreakdown calculateTicketPrice(Showtime showtime, SeatType seatType) {
        TicketPricingBreakdown baseBreakdown = calculateShowtimeBaseBreakdown(showtime);
        return calculateTicketPrice(baseBreakdown, seatType);
    }

    /**
     * Tính giá vé cụ thể cho từng ghế dựa trên cấu phần giá suất chiếu và loại ghế (SeatType).
     *
     * Công thức cốt lõi theo sức chứa (Capacity-Aware Dynamic Pricing):
     *   seatBasePrice = basePrice * capacity
     *   finalPrice = seatBasePrice + seatModifier + dayModifier + timeSlotModifier
     *
     * @param baseBreakdown Cấu phần giá cơ sở của suất chiếu (basePrice, dayModifier, timeSlotModifier)
     * @param seatType Loại ghế khách chọn (chứa sức chứa capacity và phụ thu seatModifier)
     * @return Cấu phần chi tiết và tổng giá cuối cùng
     */
    @Override
    public TicketPricingBreakdown calculateTicketPrice(TicketPricingBreakdown baseBreakdown, SeatType seatType) {
        if (baseBreakdown == null) {
            baseBreakdown = TicketPricingBreakdown.builder()
                    .basePrice(BigDecimal.ZERO)
                    .seatTypeModifier(BigDecimal.ZERO)
                    .dayModifier(BigDecimal.ZERO)
                    .timeSlotModifier(BigDecimal.ZERO)
                    .finalPrice(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal basePrice = baseBreakdown.getBasePrice() != null ? baseBreakdown.getBasePrice() : BigDecimal.ZERO;

        // Xác định sức chứa: Mặc định là 1 nếu là ghế Standard/VIP, là 2 nếu là ghế Couple
        int capacity = (seatType != null && seatType.getCapacity() != null && seatType.getCapacity() > 0)
                ? seatType.getCapacity()
                : 1;

        // Giá gốc được nhân tương ứng với số người mà ghế phục vụ
        BigDecimal seatBasePrice = basePrice.multiply(BigDecimal.valueOf(capacity));

        // Phụ thu loại ghế (ví dụ ghế VIP hoặc ghế Couple)
        BigDecimal seatModifier = (seatType != null && seatType.getPriceModifier() != null)
                ? seatType.getPriceModifier()
                : BigDecimal.ZERO;

        BigDecimal dayModifier = baseBreakdown.getDayModifier() != null ? baseBreakdown.getDayModifier() : BigDecimal.ZERO;
        BigDecimal timeSlotModifier = baseBreakdown.getTimeSlotModifier() != null ? baseBreakdown.getTimeSlotModifier() : BigDecimal.ZERO;

        // Tổng giá cuối cùng của vé
        BigDecimal totalPrice = seatBasePrice
                .add(seatModifier)
                .add(dayModifier)
                .add(timeSlotModifier);

        BigDecimal finalPrice = totalPrice.max(BigDecimal.ZERO);

        return TicketPricingBreakdown.builder()
                .basePrice(basePrice)
                .seatTypeModifier(seatModifier)
                .dayModifier(dayModifier)
                .dayOfWeek(baseBreakdown.getDayOfWeek())
                .timeSlotModifier(timeSlotModifier)
                .timeSlotRange(baseBreakdown.getTimeSlotRange())
                .finalPrice(finalPrice)
                .build();
    }

    /**
     * Xem trước bảng giá vé cho tất cả các loại ghế có trong hệ thống đối với một suất chiếu cụ thể.
     *
     * @param showtimeId Mã suất chiếu
     * @return Bảng giá chi tiết cho từng loại ghế (Standard, VIP, Couple)
     */
    @Override
    @Transactional(readOnly = true)
    public ShowtimePricingPreviewResponse previewShowtimePricing(String showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu với ID: " + showtimeId));

        TicketPricingBreakdown baseBreakdown = calculateShowtimeBaseBreakdown(showtime);
        List<SeatType> activeSeatTypes = seatTypeRepository.findAll();

        List<TicketPricingBreakdown> seatBreakdowns = activeSeatTypes.stream()
                .map(st -> calculateTicketPrice(baseBreakdown, st))
                .toList();

        return ShowtimePricingPreviewResponse.builder()
                .showtimeId(showtime.getId())
                .baseBreakdown(baseBreakdown)
                .seatTypeBreakdowns(seatBreakdowns)
                .build();
    }

    /**
     * Tính toán giá vé tối thiểu (khởi điểm) của một suất chiếu đã bao gồm phụ thu ngày và khung giờ.
     * Thường tương ứng với loại ghế STANDARD (capacity=1, phụ thu 0đ).
     *
     * @param showtime Suất chiếu cần tính
     * @return Giá vé thấp nhất có thể đặt cho suất chiếu
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateMinimumTicketPrice(Showtime showtime) {
        if (showtime == null) {
            return BigDecimal.ZERO;
        }

        TicketPricingBreakdown baseBreakdown = calculateShowtimeBaseBreakdown(showtime);
        List<SeatType> activeSeatTypes = seatTypeRepository.findAll();
        if (activeSeatTypes.isEmpty()) {
            return baseBreakdown.getFinalPrice();
        }

        return activeSeatTypes.stream()
                .map(st -> calculateTicketPrice(baseBreakdown, st).getFinalPrice())
                .min(BigDecimal::compareTo)
                .orElse(baseBreakdown.getFinalPrice());
    }

    /**
     * Lấy toàn bộ quy tắc phụ thu giá vé theo các thứ trong tuần (Thứ 2 đến Chủ nhật).
     * Tự động khởi tạo giá trị mặc định 0 VND nếu bảng quy tắc đang rỗng.
     *
     * @return Danh sách 7 quy tắc giá cho 7 ngày trong tuần
     */
    @Override
    @Transactional(readOnly = true)
    public List<DayPricingRuleResponse> getAllDayPricingRules() {
        initDefaultDayPricingRulesIfEmpty();
        List<DayPricingRule> rules = dayPricingRuleRepository.findAll();
        return rules.stream()
                .sorted(Comparator.comparingInt(r -> r.getDayOfWeek().getValue()))
                .map(this::toDayPricingRuleResponse)
                .toList();
    }

    /**
     * Lấy chi tiết quy tắc phụ thu của một thứ trong tuần theo ID.
     *
     * @param id Mã quy tắc giá ngày
     * @return Chi tiết quy tắc
     */
    @Override
    @Transactional(readOnly = true)
    public DayPricingRuleResponse getDayPricingRuleById(String id) {
        DayPricingRule rule = dayPricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc giá ngày với ID: " + id));
        return toDayPricingRuleResponse(rule);
    }

    /**
     * Cập nhật mức phụ thu giá vé cho một thứ trong tuần (ví dụ: tăng giá Thứ 7, Chủ nhật).
     *
     * @param id Mã quy tắc giá ngày
     * @param request Mức phụ thu mới (VND)
     * @return Quy tắc sau khi cập nhật
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DayPricingRuleResponse updateDayPricingRule(String id, UpdateDayPricingRuleRequest request) {
        DayPricingRule rule = dayPricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc giá ngày với ID: " + id));

        rule.setModifier(request.getModifier());
        DayPricingRule saved = dayPricingRuleRepository.save(rule);
        log.info("Updated day pricing rule {} ({}): modifier={}", saved.getId(), saved.getDayOfWeek(), saved.getModifier());
        return toDayPricingRuleResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DayPricingRuleResponse updateDayPricingRuleByDay(DayOfWeek dayOfWeek, BigDecimal modifier) {
        DayPricingRule rule = dayPricingRuleRepository.findByDayOfWeek(dayOfWeek)
                .orElseGet(() -> {
                    DayPricingRule newRule = new DayPricingRule();
                    newRule.setId(UUID.randomUUID().toString());
                    newRule.setDayOfWeek(dayOfWeek);
                    newRule.setCreatedAt(LocalDateTime.now());
                    newRule.setUpdatedAt(LocalDateTime.now());
                    return newRule;
                });

        rule.setModifier(modifier);
        rule.setUpdatedAt(LocalDateTime.now());
        DayPricingRule saved = dayPricingRuleRepository.save(rule);
        return toDayPricingRuleResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initDefaultDayPricingRulesIfEmpty() {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (!dayPricingRuleRepository.existsByDayOfWeek(day)) {
                DayPricingRule rule = new DayPricingRule();
                rule.setId(UUID.randomUUID().toString());
                rule.setDayOfWeek(day);
                rule.setModifier(BigDecimal.ZERO);
                rule.setCreatedAt(LocalDateTime.now());
                rule.setUpdatedAt(LocalDateTime.now());
                dayPricingRuleRepository.save(rule);
                log.info("Initialized default day pricing rule for {}", day);
            }
        }
    }

    /**
     * Lấy toàn bộ danh sách quy tắc phụ thu theo khung giờ chiếu (sắp xếp theo startTime tăng dần).
     *
     * @return Danh sách các quy tắc khung giờ
     */
    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotPricingRuleResponse> getAllTimeSlotPricingRules() {
        return timeSlotPricingRuleRepository.findAllByOrderByStartTimeAsc().stream()
                .map(this::toTimeSlotPricingRuleResponse)
                .toList();
    }

    /**
     * Lấy chi tiết quy tắc phụ thu khung giờ theo ID.
     *
     * @param id Mã quy tắc khung giờ
     * @return Chi tiết quy tắc
     */
    @Override
    @Transactional(readOnly = true)
    public TimeSlotPricingRuleResponse getTimeSlotPricingRuleById(String id) {
        TimeSlotPricingRule rule = timeSlotPricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc khung giờ với ID: " + id));
        return toTimeSlotPricingRuleResponse(rule);
    }

    /**
     * Tạo mới một quy tắc phụ thu theo khung giờ chiếu (ví dụ: Giờ vàng 18:00 - 22:00 +10.000 VND).
     * Kiểm tra chống trùng lấn hoặc giao nhau với các khung giờ đã tồn tại trước đó.
     *
     * @param request Thông tin khung giờ và mức phụ thu
     * @return Quy tắc vừa tạo
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TimeSlotPricingRuleResponse createTimeSlotPricingRule(CreateTimeSlotPricingRuleRequest request) {
        validateTimeSlot(null, request.getStartTime(), request.getEndTime());

        TimeSlotPricingRule rule = new TimeSlotPricingRule();
        rule.setId(UUID.randomUUID().toString());
        rule.setStartTime(request.getStartTime());
        rule.setEndTime(request.getEndTime());
        rule.setModifier(request.getModifier());
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());

        TimeSlotPricingRule saved = timeSlotPricingRuleRepository.save(rule);
        log.info("Created time slot pricing rule {}: [{} - {}], modifier={}", saved.getId(), saved.getStartTime(), saved.getEndTime(), saved.getModifier());
        return toTimeSlotPricingRuleResponse(saved);
    }

    /**
     * Cập nhật quy tắc khung giờ chiếu đã có.
     *
     * @param id Mã quy tắc
     * @param request Dữ liệu cập nhật
     * @return Quy tắc sau khi cập nhật
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TimeSlotPricingRuleResponse updateTimeSlotPricingRule(String id, UpdateTimeSlotPricingRuleRequest request) {
        TimeSlotPricingRule rule = timeSlotPricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc khung giờ với ID: " + id));

        validateTimeSlot(id, request.getStartTime(), request.getEndTime());

        rule.setStartTime(request.getStartTime());
        rule.setEndTime(request.getEndTime());
        rule.setModifier(request.getModifier());
        rule.setUpdatedAt(LocalDateTime.now());

        TimeSlotPricingRule saved = timeSlotPricingRuleRepository.save(rule);
        log.info("Updated time slot pricing rule {}: [{} - {}], modifier={}", saved.getId(), saved.getStartTime(), saved.getEndTime(), saved.getModifier());
        return toTimeSlotPricingRuleResponse(saved);
    }

    /**
     * Xóa một quy tắc phụ thu khung giờ chiếu.
     *
     * @param id Mã quy tắc cần xóa
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTimeSlotPricingRule(String id) {
        TimeSlotPricingRule rule = timeSlotPricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc khung giờ với ID: " + id));

        timeSlotPricingRuleRepository.delete(rule);
        log.info("Deleted time slot pricing rule {}", id);
    }

    /**
     * Kiểm tra tính hợp lệ của khung giờ: startTime < endTime và không giao nhau với bất kỳ khung giờ nào khác.
     */
    private void validateTimeSlot(String id, LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Thời gian bắt đầu và kết thúc không được để trống.");
        }

        if (!startTime.isBefore(endTime)) {
            throw new BadRequestException("Thời gian bắt đầu (" + startTime + ") phải trước thời gian kết thúc (" + endTime + ").");
        }

        List<TimeSlotPricingRule> overlapping = timeSlotPricingRuleRepository.findOverlappingRules(id, startTime, endTime);
        if (!overlapping.isEmpty()) {
            TimeSlotPricingRule conflict = overlapping.get(0);
            throw new ConflictException(String.format("Khung giờ [%s - %s) bị trùng lặp với khung giờ hiện có [%s - %s).",
                    startTime, endTime, conflict.getStartTime(), conflict.getEndTime()));
        }
    }

    private DayPricingRuleResponse toDayPricingRuleResponse(DayPricingRule rule) {
        if (rule == null) return null;
        return DayPricingRuleResponse.builder()
                .id(rule.getId())
                .dayOfWeek(rule.getDayOfWeek())
                .modifier(rule.getModifier())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private TimeSlotPricingRuleResponse toTimeSlotPricingRuleResponse(TimeSlotPricingRule rule) {
        if (rule == null) return null;
        return TimeSlotPricingRuleResponse.builder()
                .id(rule.getId())
                .startTime(rule.getStartTime())
                .endTime(rule.getEndTime())
                .modifier(rule.getModifier())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}

