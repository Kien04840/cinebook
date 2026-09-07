package com.cinebook.service;

import com.cinebook.dto.request.CreateTimeSlotPricingRuleRequest;
import com.cinebook.dto.request.UpdateDayPricingRuleRequest;
import com.cinebook.dto.request.UpdateTimeSlotPricingRuleRequest;
import com.cinebook.dto.response.DayPricingRuleResponse;
import com.cinebook.dto.response.ShowtimePricingPreviewResponse;
import com.cinebook.dto.response.TicketPricingBreakdown;
import com.cinebook.dto.response.TimeSlotPricingRuleResponse;
import com.cinebook.entity.SeatType;
import com.cinebook.entity.Showtime;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

public interface PricingService {

    /**
     * Single Source of Truth for base pricing breakdown of a showtime (base price + day modifier + time slot modifier).
     */
    TicketPricingBreakdown calculateShowtimeBaseBreakdown(Showtime showtime);

    /**
     * Single Source of Truth for final ticket pricing calculation according to CineBook V1 formula:
     * Ticket Price = Showtime Base Price + Seat Type Modifier + Day Pricing Modifier + Time Slot Pricing Modifier
     */
    TicketPricingBreakdown calculateTicketPrice(Showtime showtime, SeatType seatType);

    /**
     * Efficiently calculates ticket price using precomputed showtime base breakdown.
     */
    TicketPricingBreakdown calculateTicketPrice(TicketPricingBreakdown baseBreakdown, SeatType seatType);

    /**
     * Previews pricing breakdown for a showtime across all active seat types.
     */
    ShowtimePricingPreviewResponse previewShowtimePricing(String showtimeId);

    // Day pricing rules
    List<DayPricingRuleResponse> getAllDayPricingRules();

    DayPricingRuleResponse getDayPricingRuleById(String id);

    DayPricingRuleResponse updateDayPricingRule(String id, UpdateDayPricingRuleRequest request);

    DayPricingRuleResponse updateDayPricingRuleByDay(DayOfWeek dayOfWeek, BigDecimal modifier);

    void initDefaultDayPricingRulesIfEmpty();

    // Time slot pricing rules
    List<TimeSlotPricingRuleResponse> getAllTimeSlotPricingRules();

    TimeSlotPricingRuleResponse getTimeSlotPricingRuleById(String id);

    TimeSlotPricingRuleResponse createTimeSlotPricingRule(CreateTimeSlotPricingRuleRequest request);

    TimeSlotPricingRuleResponse updateTimeSlotPricingRule(String id, UpdateTimeSlotPricingRuleRequest request);

    void deleteTimeSlotPricingRule(String id);
}

