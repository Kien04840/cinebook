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

/**
 * Controller quản trị các quy tắc định giá động (Admin Dynamic Pricing Controller).
 *
 * Chịu trách nhiệm:
 * - Cấu hình mức phụ thu giá vé theo các thứ trong tuần (Thứ 2 - Chủ nhật).
 * - Cấu hình mức phụ thu giá vé theo khung giờ chiếu (Khung giờ sáng, chiều, tối, giờ vàng).
 * - Xem trước cấu phần chi tiết giá vé cho từng loại ghế của một suất chiếu.
 */
@Tag(name = "Admin Pricing", description = "Quản trị quy tắc định giá vé động (theo ngày trong tuần và khung giờ chiếu)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/pricing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {

    private final PricingService pricingService;

    // --- QUY TẮC GIÁ THEO THỨ TRONG TUẦN ---

    @Operation(summary = "Lấy danh sách quy tắc phụ thu cho tất cả các ngày trong tuần (Thứ 2 đến Chủ nhật)")
    @GetMapping("/day-rules")
    public ResponseEntity<List<DayPricingRuleResponse>> getAllDayPricingRules() {
        return ResponseEntity.ok(pricingService.getAllDayPricingRules());
    }

    @Operation(summary = "Lấy thông tin quy tắc phụ thu ngày theo ID")
    @GetMapping("/day-rules/{id}")
    public ResponseEntity<DayPricingRuleResponse> getDayPricingRuleById(@PathVariable String id) {
        return ResponseEntity.ok(pricingService.getDayPricingRuleById(id));
    }

    @Operation(summary = "Cập nhật mức phụ thu cho một ngày trong tuần theo ID quy tắc")
    @PutMapping("/day-rules/{id}")
    public ResponseEntity<DayPricingRuleResponse> updateDayPricingRule(
            @PathVariable String id,
            @Valid @RequestBody UpdateDayPricingRuleRequest request
    ) {
        return ResponseEntity.ok(pricingService.updateDayPricingRule(id, request));
    }

    @Operation(summary = "Cập nhật trực tiếp mức phụ thu cho một thứ trong tuần")
    @PutMapping("/day-rules/by-day/{dayOfWeek}")
    public ResponseEntity<DayPricingRuleResponse> updateDayPricingRuleByDay(
            @PathVariable DayOfWeek dayOfWeek,
            @RequestParam BigDecimal modifier
    ) {
        return ResponseEntity.ok(pricingService.updateDayPricingRuleByDay(dayOfWeek, modifier));
    }

    // --- QUY TẮC GIÁ THEO KHUNG GIỜ CHIẾU ---

    @Operation(summary = "Lấy danh sách tất cả các quy tắc phụ thu theo khung giờ chiếu")
    @GetMapping({"/time-slots", "/time-slot-rules"})
    public ResponseEntity<List<TimeSlotPricingRuleResponse>> getAllTimeSlotPricingRules() {
        return ResponseEntity.ok(pricingService.getAllTimeSlotPricingRules());
    }

    @Operation(summary = "Lấy thông tin chi tiết một quy tắc khung giờ chiếu theo ID")
    @GetMapping({"/time-slots/{id}", "/time-slot-rules/{id}"})
    public ResponseEntity<TimeSlotPricingRuleResponse> getTimeSlotPricingRuleById(@PathVariable String id) {
        return ResponseEntity.ok(pricingService.getTimeSlotPricingRuleById(id));
    }

    @Operation(summary = "Tạo mới một quy tắc phụ thu theo khung giờ chiếu")
    @PostMapping({"/time-slots", "/time-slot-rules"})
    public ResponseEntity<TimeSlotPricingRuleResponse> createTimeSlotPricingRule(
            @Valid @RequestBody CreateTimeSlotPricingRuleRequest request
    ) {
        TimeSlotPricingRuleResponse response = pricingService.createTimeSlotPricingRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Cập nhật quy tắc phụ thu khung giờ chiếu theo ID")
    @PutMapping({"/time-slots/{id}", "/time-slot-rules/{id}"})
    public ResponseEntity<TimeSlotPricingRuleResponse> updateTimeSlotPricingRule(
            @PathVariable String id,
            @Valid @RequestBody UpdateTimeSlotPricingRuleRequest request
    ) {
        return ResponseEntity.ok(pricingService.updateTimeSlotPricingRule(id, request));
    }

    @Operation(summary = "Xóa một quy tắc phụ thu khung giờ chiếu theo ID")
    @DeleteMapping({"/time-slots/{id}", "/time-slot-rules/{id}"})
    public ResponseEntity<Void> deleteTimeSlotPricingRule(@PathVariable String id) {
        pricingService.deleteTimeSlotPricingRule(id);
        return ResponseEntity.noContent().build();
    }

    // --- XEM TRƯỚC BẢNG GIÁ ---

    @Operation(summary = "Xem trước cấu phần giá vé chi tiết cho một suất chiếu")
    @GetMapping("/preview/{showtimeId}")
    public ResponseEntity<ShowtimePricingPreviewResponse> previewShowtimePricing(@PathVariable String showtimeId) {
        return ResponseEntity.ok(pricingService.previewShowtimePricing(showtimeId));
    }
}

