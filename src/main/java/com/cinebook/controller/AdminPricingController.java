package com.cinebook.controller;

import com.cinebook.dto.request.CreateTimeSlotPricingRuleRequest;
import com.cinebook.dto.request.UpdateDayPricingRuleRequest;
import com.cinebook.dto.request.UpdateTimeSlotPricingRuleRequest;
import com.cinebook.dto.response.DayPricingRuleResponse;
import com.cinebook.dto.response.ShowtimePricingPreviewResponse;
import com.cinebook.dto.response.TimeSlotPricingRuleResponse;
import com.cinebook.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

@Tag(name = "Admin Pricing", description = "Administrator dynamic ticket pricing rules (Day of week & Time slot)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/pricing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {

    private final PricingService pricingService;

    // --- DAY PRICING RULES ---

    @Operation(summary = "Get all weekday pricing rules (Monday to Sunday)")
    @GetMapping("/day-rules")
    public ResponseEntity<List<DayPricingRuleResponse>> getAllDayPricingRules() {
        return ResponseEntity.ok(pricingService.getAllDayPricingRules());
    }

    @Operation(summary = "Get a specific weekday pricing rule by ID")
    @GetMapping("/day-rules/{id}")
    public ResponseEntity<DayPricingRuleResponse> getDayPricingRuleById(@PathVariable String id) {
        return ResponseEntity.ok(pricingService.getDayPricingRuleById(id));
    }

    @Operation(summary = "Update price modifier for a specific weekday rule by ID")
    @PutMapping("/day-rules/{id}")
    public ResponseEntity<DayPricingRuleResponse> updateDayPricingRule(
            @PathVariable String id,
            @Valid @RequestBody UpdateDayPricingRuleRequest request
    ) {
        return ResponseEntity.ok(pricingService.updateDayPricingRule(id, request));
    }

    @Operation(summary = "Update price modifier for a day of week directly")
    @PutMapping("/day-rules/by-day/{dayOfWeek}")
    public ResponseEntity<DayPricingRuleResponse> updateDayPricingRuleByDay(
            @PathVariable DayOfWeek dayOfWeek,
            @RequestParam BigDecimal modifier
    ) {
        return ResponseEntity.ok(pricingService.updateDayPricingRuleByDay(dayOfWeek, modifier));
    }

    // --- TIME SLOT PRICING RULES ---

    @Operation(summary = "Get all time slot pricing rules")
    @GetMapping({"/time-slots", "/time-slot-rules"})
    public ResponseEntity<List<TimeSlotPricingRuleResponse>> getAllTimeSlotPricingRules() {
        return ResponseEntity.ok(pricingService.getAllTimeSlotPricingRules());
    }

    @Operation(summary = "Get a specific time slot pricing rule by ID")
    @GetMapping({"/time-slots/{id}", "/time-slot-rules/{id}"})
    public ResponseEntity<TimeSlotPricingRuleResponse> getTimeSlotPricingRuleById(@PathVariable String id) {
        return ResponseEntity.ok(pricingService.getTimeSlotPricingRuleById(id));
    }

    @Operation(summary = "Create a new time slot pricing rule")
    @PostMapping({"/time-slots", "/time-slot-rules"})
    public ResponseEntity<TimeSlotPricingRuleResponse> createTimeSlotPricingRule(
            @Valid @RequestBody CreateTimeSlotPricingRuleRequest request
    ) {
        TimeSlotPricingRuleResponse response = pricingService.createTimeSlotPricingRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update an existing time slot pricing rule")
    @PutMapping({"/time-slots/{id}", "/time-slot-rules/{id}"})
    public ResponseEntity<TimeSlotPricingRuleResponse> updateTimeSlotPricingRule(
            @PathVariable String id,
            @Valid @RequestBody UpdateTimeSlotPricingRuleRequest request
    ) {
        return ResponseEntity.ok(pricingService.updateTimeSlotPricingRule(id, request));
    }

    @Operation(summary = "Delete a time slot pricing rule")
    @DeleteMapping({"/time-slots/{id}", "/time-slot-rules/{id}"})
    public ResponseEntity<Void> deleteTimeSlotPricingRule(@PathVariable String id) {
        pricingService.deleteTimeSlotPricingRule(id);
        return ResponseEntity.noContent().build();
    }

    // --- PREVIEW ---

    @Operation(summary = "Preview pricing breakdown for a showtime")
    @GetMapping("/preview/{showtimeId}")
    public ResponseEntity<ShowtimePricingPreviewResponse> previewShowtimePricing(@PathVariable String showtimeId) {
        return ResponseEntity.ok(pricingService.previewShowtimePricing(showtimeId));
    }
}

