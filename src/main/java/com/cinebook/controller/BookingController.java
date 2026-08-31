package com.cinebook.controller;

import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.request.CreateBookingRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Booking", description = "Customer booking and seat reservation endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Start booking / hold seats with 5-minute reservation window")
    @PostMapping
    public ResponseEntity<BookingDetailResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingDetailResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get paginated booking history of authenticated customer")
    @GetMapping("/me")
    public ResponseEntity<PageResponse<BookingSummaryResponse>> getMyBookings(
            @RequestParam(name = "status", required = false) BookingStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<BookingSummaryResponse> response = bookingService.getMyBookings(status, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get detailed booking information by ID (Owner or Admin)")
    @GetMapping("/{id}")
    public ResponseEntity<BookingDetailResponse> getBookingDetail(@PathVariable String id) {
        BookingDetailResponse response = bookingService.getBookingDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel unpaid pending booking and release held seats")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingDetailResponse> cancelBooking(
            @PathVariable String id,
            @Valid @RequestBody(required = false) CancelBookingRequest request
    ) {
        BookingDetailResponse response = bookingService.cancelBooking(id, request);
        return ResponseEntity.ok(response);
    }
}

