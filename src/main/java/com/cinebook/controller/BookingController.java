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

/**
 * Controller xử lý các yêu cầu đặt vé của khách hàng (Customer Booking Controller).
 * Tất cả endpoints đều yêu cầu đăng nhập và có token Bearer hợp lệ.
 * 
 * Các chức năng chính:
 * - Tạo đơn đặt vé & giữ chỗ 5 phút (POST /api/v1/bookings)
 * - Lịch sử đặt vé cá nhân phân trang (GET /api/v1/bookings/me)
 * - Tra cứu đơn giữ chỗ đang hoạt động của khách cho suất chiếu (GET /api/v1/bookings/active)
 * - Xem chi tiết đơn hàng (GET /api/v1/bookings/{id})
 * - Khách chủ động hủy đơn giữ chỗ chưa thanh toán (POST /api/v1/bookings/{id}/cancel)
 */
@Tag(name = "Booking", description = "Các API đặt vé và giữ chỗ xem phim dành cho khách hàng")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * Khởi tạo đơn đặt vé và kích hoạt cơ chế giữ chỗ tạm thời trong 5 phút.
     * Trả về HTTP 201 CREATED kèm thông tin đơn hàng, danh sách ghế giữ chỗ và thời điểm hết hạn.
     */
    @Operation(summary = "Tạo đơn đặt vé mới và giữ chỗ tạm thời trong 5 phút")
    @PostMapping
    public ResponseEntity<BookingDetailResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingDetailResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lấy danh sách lịch sử đặt vé có phân trang của người dùng hiện tại đang đăng nhập.
     */
    @Operation(summary = "Lấy danh sách lịch sử đặt vé có phân trang của khách hàng")
    @GetMapping("/me")
    public ResponseEntity<PageResponse<BookingSummaryResponse>> getMyBookings(
            @RequestParam(name = "status", required = false) BookingStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<BookingSummaryResponse> response = bookingService.getMyBookings(status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Tra cứu đơn đặt vé đang chờ thanh toán (PENDING_PAYMENT) của khách hàng cho suất chiếu cụ thể.
     */
    @Operation(summary = "Tra cứu đơn giữ chỗ đang chờ thanh toán của người dùng cho suất chiếu")
    @GetMapping("/active")
    public ResponseEntity<BookingDetailResponse> getActiveBooking(
            @RequestParam(name = "showtimeId") String showtimeId
    ) {
        BookingDetailResponse response = bookingService.getActiveBookingForShowtime(showtimeId);
        return ResponseEntity.ok(response);
    }

    /**
     * Xem thông tin chi tiết của một đơn đặt vé theo mã định danh ID.
     */
    @Operation(summary = "Xem thông tin chi tiết đơn đặt vé theo ID (chính chủ hoặc Admin)")
    @GetMapping("/{id}")
    public ResponseEntity<BookingDetailResponse> getBookingDetail(@PathVariable String id) {
        BookingDetailResponse response = bookingService.getBookingDetail(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Khách hàng hoặc Admin chủ động hủy đơn đặt vé đang chờ thanh toán để giải phóng ghế.
     */
    @Operation(summary = "Hủy đơn đặt vé chưa thanh toán và giải phóng ghế giữ chỗ")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingDetailResponse> cancelBooking(
            @PathVariable String id,
            @Valid @RequestBody(required = false) CancelBookingRequest request
    ) {
        BookingDetailResponse response = bookingService.cancelBooking(id, request);
        return ResponseEntity.ok(response);
    }
}

