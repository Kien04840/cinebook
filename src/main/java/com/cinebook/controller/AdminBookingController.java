package com.cinebook.controller;

import com.cinebook.dto.request.BookingCheckInRequest;
import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.response.*;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Booking", description = "Administrator booking search, check-in, and management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingService bookingService;

    @Operation(summary = "Verify booking details and check-in eligibility by check-in code")
    @GetMapping("/verify")
    public ResponseEntity<BookingVerifyResponse> verifyBookingCheckIn(
            @RequestParam(name = "code") String code
    ) {
        BookingVerifyResponse response = bookingService.verifyBookingCheckIn(code);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Perform atomic check-in for all eligible tickets in a booking by check-in code")
    @PostMapping("/check-in")
    public ResponseEntity<BookingCheckInResponse> checkInBooking(
            @Valid @RequestBody BookingCheckInRequest request
    ) {
        BookingCheckInResponse response = bookingService.checkInBooking(request);
        return ResponseEntity.ok(response);
    }

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