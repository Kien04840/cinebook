package com.cinebook.service;

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
import com.cinebook.repository.DayPricingRuleRepository;
import com.cinebook.repository.SeatTypeRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.repository.TimeSlotPricingRuleRepository;
import com.cinebook.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private DayPricingRuleRepository dayPricingRuleRepository;

    @Mock
    private TimeSlotPricingRuleRepository timeSlotPricingRuleRepository;

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private SeatTypeRepository seatTypeRepository;

    private PricingServiceImpl pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingServiceImpl(
                dayPricingRuleRepository,
                timeSlotPricingRuleRepository,
                showtimeRepository,
                seatTypeRepository
        );
    }

    @Test
    @DisplayName("Formula: Base price only when no modifiers are configured")
    void testCalculateTicketPrice_BasePriceOnly() {
        Showtime showtime = new Showtime();
        showtime.setBasePrice(new BigDecimal("75000"));
        showtime.setStartTime(LocalDateTime.of(2026, 9, 7, 10, 0)); // Monday 10:00

        when(dayPricingRuleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(Optional.empty());
        when(timeSlotPricingRuleRepository.findApplicableRules(LocalTime.of(10, 0))).thenReturn(Collections.emptyList());

        TicketPricingBreakdown result = pricingService.calculateTicketPrice(showtime, null);

        assertThat(result.getBasePrice()).isEqualByComparingTo("75000");
        assertThat(result.getSeatTypeModifier()).isEqualByComparingTo("0");
        assertThat(result.getDayModifier()).isEqualByComparingTo("0");
        assertThat(result.getTimeSlotModifier()).isEqualByComparingTo("0");
        assertThat(result.getFinalPrice()).isEqualByComparingTo("75000");
    }

    @Test
    @DisplayName("Formula: Base price + Seat type modifier (VIP seat)")
    void testCalculateTicketPrice_BasePlusSeatModifier() {
        Showtime showtime = new Showtime();
        showtime.setBasePrice(new BigDecimal("75000"));
        showtime.setStartTime(LocalDateTime.of(2026, 9, 7, 10, 0));

        SeatType vipSeat = new SeatType();
        vipSeat.setCode("VIP");
        vipSeat.setPriceModifier(new BigDecimal("15000"));

        when(dayPricingRuleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(Optional.empty());
        when(timeSlotPricingRuleRepository.findApplicableRules(LocalTime.of(10, 0))).thenReturn(Collections.emptyList());

        TicketPricingBreakdown result = pricingService.calculateTicketPrice(showtime, vipSeat);

        assertThat(result.getBasePrice()).isEqualByComparingTo("75000");
        assertThat(result.getSeatTypeModifier()).isEqualByComparingTo("15000");
        assertThat(result.getFinalPrice()).isEqualByComparingTo("90000");
    }

    @Test
    @DisplayName("Formula: Base price + Day modifier (Saturday weekend surcharge)")
    void testCalculateTicketPrice_BasePlusDayModifier() {
        Showtime showtime = new Showtime();
        showtime.setBasePrice(new BigDecimal("75000"));
        showtime.setStartTime(LocalDateTime.of(2026, 9, 12, 10, 0)); // Saturday

        DayPricingRule saturdayRule = new DayPricingRule();
        saturdayRule.setDayOfWeek(DayOfWeek.SATURDAY);
        saturdayRule.setModifier(new BigDecimal("10000"));

        when(dayPricingRuleRepository.findByDayOfWeek(DayOfWeek.SATURDAY)).thenReturn(Optional.of(saturdayRule));
        when(timeSlotPricingRuleRepository.findApplicableRules(LocalTime.of(10, 0))).thenReturn(Collections.emptyList());

        TicketPricingBreakdown result = pricingService.calculateTicketPrice(showtime, null);

        assertThat(result.getDayModifier()).isEqualByComparingTo("10000");
        assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(result.getFinalPrice()).isEqualByComparingTo("85000");
    }

    @Test
    @DisplayName("Formula: Full combination: Base + SeatType + Day + TimeSlot")
    void testCalculateTicketPrice_FullCombination() {
        // Saturday at 19:30: Prime time + weekend + VIP seat
        Showtime showtime = new Showtime();
        showtime.setBasePrice(new BigDecimal("75000"));
        showtime.setStartTime(LocalDateTime.of(2026, 9, 12, 19, 30));

        SeatType vipSeat = new SeatType();
        vipSeat.setCode("VIP");
        vipSeat.setPriceModifier(new BigDecimal("15000"));

        DayPricingRule satRule = new DayPricingRule();
        satRule.setDayOfWeek(DayOfWeek.SATURDAY);
        satRule.setModifier(new BigDecimal("10000"));

        TimeSlotPricingRule primeSlot = new TimeSlotPricingRule();
        primeSlot.setStartTime(LocalTime.of(18, 0));
        primeSlot.setEndTime(LocalTime.of(22, 0));
        primeSlot.setModifier(new BigDecimal("15000"));

        when(dayPricingRuleRepository.findByDayOfWeek(DayOfWeek.SATURDAY)).thenReturn(Optional.of(satRule));
        when(timeSlotPricingRuleRepository.findApplicableRules(LocalTime.of(19, 30))).thenReturn(List.of(primeSlot));

        TicketPricingBreakdown result = pricingService.calculateTicketPrice(showtime, vipSeat);

        assertThat(result.getBasePrice()).isEqualByComparingTo("75000");
        assertThat(result.getSeatTypeModifier()).isEqualByComparingTo("15000");
        assertThat(result.getDayModifier()).isEqualByComparingTo("10000");
        assertThat(result.getTimeSlotModifier()).isEqualByComparingTo("15000");
        assertThat(result.getTimeSlotRange()).isEqualTo("18:00 - 22:00");
        // 75k + 15k + 10k + 15k = 115k
        assertThat(result.getFinalPrice()).isEqualByComparingTo("115000");
    }

    @Test
    @DisplayName("Formula: Capacity-aware Couple seat calculation: (Base * capacity) + seatModifier + day + time")
    void testCalculateTicketPrice_CoupleSeatCapacityAware() {
        // Showtime base price 90,000 VND
        Showtime showtime = new Showtime();
        showtime.setBasePrice(new BigDecimal("90000"));
        showtime.setStartTime(LocalDateTime.of(2026, 9, 12, 19, 30)); // Saturday 19:30

        SeatType coupleSeat = new SeatType();
        coupleSeat.setCode("COUPLE");
        coupleSeat.setCapacity((short) 2);
        coupleSeat.setPriceModifier(new BigDecimal("40000"));

        DayPricingRule satRule = new DayPricingRule();
        satRule.setDayOfWeek(DayOfWeek.SATURDAY);
        satRule.setModifier(new BigDecimal("5000"));

        TimeSlotPricingRule primeSlot = new TimeSlotPricingRule();
        primeSlot.setStartTime(LocalTime.of(18, 0));
        primeSlot.setEndTime(LocalTime.of(22, 0));
        primeSlot.setModifier(new BigDecimal("15000"));

        when(dayPricingRuleRepository.findByDayOfWeek(DayOfWeek.SATURDAY)).thenReturn(Optional.of(satRule));
        when(timeSlotPricingRuleRepository.findApplicableRules(LocalTime.of(19, 30))).thenReturn(List.of(primeSlot));

        TicketPricingBreakdown result = pricingService.calculateTicketPrice(showtime, coupleSeat);

        // (90,000 * 2) + 40,000 + 5,000 + 15,000 = 180,000 + 60,000 = 240,000
        assertThat(result.getBasePrice()).isEqualByComparingTo("90000");
        assertThat(result.getSeatTypeModifier()).isEqualByComparingTo("40000");
        assertThat(result.getDayModifier()).isEqualByComparingTo("5000");
        assertThat(result.getTimeSlotModifier()).isEqualByComparingTo("15000");
        assertThat(result.getFinalPrice()).isEqualByComparingTo("240000");
    }

    @Test
    @DisplayName("Formula: Canonical base price 90k comparison across Standard (90k), VIP (110k), Couple (220k)")
    void testCalculateTicketPrice_CanonicalTiersComparison() {
        Showtime showtime = new Showtime();
        showtime.setBasePrice(new BigDecimal("90000"));
        showtime.setStartTime(LocalDateTime.of(2026, 9, 7, 10, 0)); // Monday 10:00 (no modifiers)

        when(dayPricingRuleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(Optional.empty());
        when(timeSlotPricingRuleRepository.findApplicableRules(LocalTime.of(10, 0))).thenReturn(Collections.emptyList());

        SeatType standard = new SeatType();
        standard.setCode("STANDARD");
        standard.setCapacity((short) 1);
        standard.setPriceModifier(BigDecimal.ZERO);

        SeatType vip = new SeatType();
        vip.setCode("VIP");
        vip.setCapacity((short) 1);
        vip.setPriceModifier(new BigDecimal("20000"));

        SeatType couple = new SeatType();
        couple.setCode("COUPLE");
        couple.setCapacity((short) 2);
        couple.setPriceModifier(new BigDecimal("40000"));

        TicketPricingBreakdown standardRes = pricingService.calculateTicketPrice(showtime, standard);
        TicketPricingBreakdown vipRes = pricingService.calculateTicketPrice(showtime, vip);
        TicketPricingBreakdown coupleRes = pricingService.calculateTicketPrice(showtime, couple);

        // Standard: 90k * 1 + 0 = 90k
        assertThat(standardRes.getFinalPrice()).isEqualByComparingTo("90000");
        // VIP: 90k * 1 + 20k = 110k
        assertThat(vipRes.getFinalPrice()).isEqualByComparingTo("110000");
        // Couple: 90k * 2 + 40k = 220k
        assertThat(coupleRes.getFinalPrice()).isEqualByComparingTo("220000");
    }

    @Test
    @DisplayName("Time Slot: [startTime, endTime) half-open interval boundaries")
    void testTimeSlotBoundaryConditions() {
        TimeSlotPricingRule morningSlot = new TimeSlotPricingRule();
        morningSlot.setStartTime(LocalTime.of(8, 0));
        morningSlot.setEndTime(LocalTime.of(12, 0));
        morningSlot.setModifier(new BigDecimal("5000"));

        TimeSlotPricingRule afternoonSlot = new TimeSlotPricingRule();
        afternoonSlot.setStartTime(LocalTime.of(12, 0));
        afternoonSlot.setEndTime(LocalTime.of(17, 0));
        afternoonSlot.setModifier(new BigDecimal("8000"));

        Showtime stMorningStart = new Showtime();
        stMorningStart.setBasePrice(new BigDecimal("70000"));
        stMorningStart.setStartTime(LocalDateTime.of(2026, 9, 8, 8, 0, 0)); // 08:00:00 exact start

        when(timeSlotPricingRuleRepository.findApplicableRules(LocalTime.of(8, 0, 0))).thenReturn(List.of(morningSlot));
        TicketPricingBreakdown res1 = pricingService.calculateTicketPrice(stMorningStart, null);
        assertThat(res1.getTimeSlotModifier()).isEqualByComparingTo("5000");

        Showtime stExact12 = new Showtime();
        stExact12.setBasePrice(new BigDecimal("70000"));
        stExact12.setStartTime(LocalDateTime.of(2026, 9, 8, 12, 0, 0)); // 12:00:00 exact boundary

        when(timeSlotPricingRuleRepository.findApplicableRules(LocalTime.of(12, 0, 0))).thenReturn(List.of(afternoonSlot));
        TicketPricingBreakdown res2 = pricingService.calculateTicketPrice(stExact12, null);
        // At 12:00, matches afternoon slot [12:00, 17:00), NOT morning slot [08:00, 12:00)
        assertThat(res2.getTimeSlotModifier()).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("Negative modifier: Allowed discounts but finalPrice is clamped to zero")
    void testNegativeModifierClampedToZero() {
        Showtime showtime = new Showtime();
        showtime.setBasePrice(new BigDecimal("40000"));
        showtime.setStartTime(LocalDateTime.of(2026, 9, 9, 9, 0)); // Wednesday 09:00

        DayPricingRule wedRule = new DayPricingRule();
        wedRule.setDayOfWeek(DayOfWeek.WEDNESDAY);
        wedRule.setModifier(new BigDecimal("-20000")); // -20k Happy Wednesday discount

        TimeSlotPricingRule earlySlot = new TimeSlotPricingRule();
        earlySlot.setStartTime(LocalTime.of(8, 0));
        earlySlot.setEndTime(LocalTime.of(11, 0));
        earlySlot.setModifier(new BigDecimal("-30000")); // -30k Early bird discount

        when(dayPricingRuleRepository.findByDayOfWeek(DayOfWeek.WEDNESDAY)).thenReturn(Optional.of(wedRule));
        when(timeSlotPricingRuleRepository.findApplicableRules(LocalTime.of(9, 0))).thenReturn(List.of(earlySlot));

        TicketPricingBreakdown result = pricingService.calculateTicketPrice(showtime, null);

        // 40,000 - 20,000 - 30,000 = -10,000 -> clamped to 0
        assertThat(result.getBasePrice()).isEqualByComparingTo("40000");
        assertThat(result.getDayModifier()).isEqualByComparingTo("-20000");
        assertThat(result.getTimeSlotModifier()).isEqualByComparingTo("-30000");
        assertThat(result.getFinalPrice()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Day Pricing: initDefaultDayPricingRulesIfEmpty is idempotent")
    void testInitDefaultDayPricingRulesIfEmpty_Idempotent() {
        when(dayPricingRuleRepository.existsByDayOfWeek(any(DayOfWeek.class))).thenReturn(false);

        pricingService.initDefaultDayPricingRulesIfEmpty();
        // 7 days saved
        verify(dayPricingRuleRepository, times(7)).save(any(DayPricingRule.class));

        // Second run: all 7 days already exist
        when(dayPricingRuleRepository.existsByDayOfWeek(any(DayOfWeek.class))).thenReturn(true);
        pricingService.initDefaultDayPricingRulesIfEmpty();
        // Still only 7 total saves, no duplicate creations
        verify(dayPricingRuleRepository, times(7)).save(any(DayPricingRule.class));
    }

    @Test
    @DisplayName("Time Slot Validation: Reject invalid range startTime >= endTime")
    void testCreateTimeSlot_InvalidRange() {
        CreateTimeSlotPricingRuleRequest req = CreateTimeSlotPricingRuleRequest.builder()
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(14, 0))
                .modifier(new BigDecimal("10000"))
                .build();

        assertThatThrownBy(() -> pricingService.createTimeSlotPricingRule(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("phải trước thời gian kết thúc");
    }

    @Test
    @DisplayName("Time Slot Validation: Reject overlapping intervals")
    void testCreateTimeSlot_RejectOverlap() {
        TimeSlotPricingRule existing = new TimeSlotPricingRule();
        existing.setId("slot-1");
        existing.setStartTime(LocalTime.of(10, 0));
        existing.setEndTime(LocalTime.of(14, 0));

        when(timeSlotPricingRuleRepository.findOverlappingRules(null, LocalTime.of(12, 0), LocalTime.of(16, 0)))
                .thenReturn(List.of(existing));

        CreateTimeSlotPricingRuleRequest req = CreateTimeSlotPricingRuleRequest.builder()
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(16, 0))
                .modifier(new BigDecimal("10000"))
                .build();

        assertThatThrownBy(() -> pricingService.createTimeSlotPricingRule(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("bị trùng lặp");
    }
}

