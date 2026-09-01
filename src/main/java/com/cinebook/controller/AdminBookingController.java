package com.cinebook.controller;

import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.response.BookingDetailResponse;
import com.cinebook.dto.response.BookingSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.BookingStatus;
import com.cinebook.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Booking", description = "Administrator booking search and management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;

    @Operation(summary = "List and search all bookings for administration")
    @GetMapping
    public ResponseEntity<PageResponse<BookingSummaryResponse>> getAdminBookings(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) BookingStatus status,
            @RequestParam(name = "showtimeId", required = false) String showtimeId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<BookingSummaryResponse> response = bookingService.getAdminBookings(q, status, showtimeId, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get detailed booking information by ID for administration")
    @GetMapping("/{id}")
    public ResponseEntity<BookingDetailResponse> getAdminBookingDetail(@PathVariable String id) {
        BookingDetailResponse response = bookingService.getBookingDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel an unpaid booking for administration")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingDetailResponse> cancelAdminBooking(
            @PathVariable String id,
            @Valid @RequestBody(required = false) CancelBookingRequest request
    ) {
        BookingDetailResponse response = bookingService.cancelBooking(id, request);
        return ResponseEntity.ok(response);
    }
}