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

    @Override
    @Transactional(readOnly = true)
    public TicketPricingBreakdown calculateTicketPrice(Showtime showtime, SeatType seatType) {
        TicketPricingBreakdown baseBreakdown = calculateShowtimeBaseBreakdown(showtime);
        return calculateTicketPrice(baseBreakdown, seatType);
    }

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

        int capacity = (seatType != null && seatType.getCapacity() != null && seatType.getCapacity() > 0)
                ? seatType.getCapacity()
                : 1;

        BigDecimal seatBasePrice = basePrice.multiply(BigDecimal.valueOf(capacity));

        BigDecimal seatModifier = (seatType != null && seatType.getPriceModifier() != null)
                ? seatType.getPriceModifier()
                : BigDecimal.ZERO;

        BigDecimal dayModifier = baseBreakdown.getDayModifier() != null ? baseBreakdown.getDayModifier() : BigDecimal.ZERO;
        BigDecimal timeSlotModifier = baseBreakdown.getTimeSlotModifier() != null ? baseBreakdown.getTimeSlotModifier() : BigDecimal.ZERO;

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

    @Override
    @Transactional(readOnly = true)
    public DayPricingRuleResponse getDayPricingRuleById(String id) {
        DayPricingRule rule = dayPricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc giá ngày với ID: " + id));
        return toDayPricingRuleResponse(rule);
    }

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

    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotPricingRuleResponse> getAllTimeSlotPricingRules() {
        return timeSlotPricingRuleRepository.findAllByOrderByStartTimeAsc().stream()
                .map(this::toTimeSlotPricingRuleResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TimeSlotPricingRuleResponse getTimeSlotPricingRuleById(String id) {
        TimeSlotPricingRule rule = timeSlotPricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc khung giờ với ID: " + id));
        return toTimeSlotPricingRuleResponse(rule);
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTimeSlotPricingRule(String id) {
        TimeSlotPricingRule rule = timeSlotPricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc khung giờ với ID: " + id));

        timeSlotPricingRuleRepository.delete(rule);
        log.info("Deleted time slot pricing rule {}", id);
    }

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

