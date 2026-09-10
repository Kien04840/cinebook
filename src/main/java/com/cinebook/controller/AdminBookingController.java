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

/**
 * Controller quản trị đơn đặt vé và soát vé vào phòng chiếu (Admin Booking Controller).
 * Chỉ người dùng có vai trò ADMIN mới được phép truy cập.
 * 
 * Các chức năng chính:
 * - Tra cứu và xác minh tính hợp lệ của mã soát vé (GET /api/v1/admin/bookings/verify)
 * - Thực hiện thao tác soát vé nguyên tử (POST /api/v1/admin/bookings/check-in)
 * - Tra cứu, tìm kiếm danh sách toàn bộ đơn vé phân trang (GET /api/v1/admin/bookings)
 * - Xem chi tiết đơn vé bất kỳ (GET /api/v1/admin/bookings/{id})
 * - Admin can thiệp hủy đơn vé chưa thanh toán (POST /api/v1/admin/bookings/{id}/cancel)
 */
@Tag(name = "Admin Booking", description = "Các API quản lý đơn đặt vé, đối soát và soát vé dành cho Quản trị viên")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingService bookingService;

    /**
     * Tra cứu thông tin đơn vé và kiểm tra điều kiện vào phòng chiếu qua mã soát vé (Check-in Verification).
     */
    @Operation(summary = "Xác minh thông tin đơn vé và điều kiện soát vé bằng mã check-in code")
    @GetMapping("/verify")
    public ResponseEntity<BookingVerifyResponse> verifyBookingCheckIn(
            @RequestParam(name = "code") String code
    ) {
        BookingVerifyResponse response = bookingService.verifyBookingCheckIn(code);
        return ResponseEntity.ok(response);
    }

    /**
     * Thực hiện soát vé nguyên tử: Chuyển toàn bộ vé hợp lệ của đơn hàng sang trạng thái USED.
     */
    @Operation(summary = "Thực hiện soát vé nguyên tử cho toàn bộ vé hợp lệ trong đơn qua mã check-in code")
    @PostMapping("/check-in")
    public ResponseEntity<BookingCheckInResponse> checkInBooking(
            @Valid @RequestBody BookingCheckInRequest request
    ) {
        BookingCheckInResponse response = bookingService.checkInBooking(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Tìm kiếm và lọc danh sách toàn bộ đơn đặt vé trong hệ thống theo từ khóa, trạng thái và suất chiếu.
     */
    @Operation(summary = "Tìm kiếm và phân trang danh sách đơn đặt vé dành cho Quản trị viên")
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

    /**
     * Xem thông tin chi tiết một đơn đặt vé bất kỳ theo mã ID.
     */
    @Operation(summary = "Xem thông tin chi tiết đơn đặt vé theo ID dành cho Quản trị viên")
    @GetMapping("/{id}")
    public ResponseEntity<BookingDetailResponse> getAdminBookingDetail(@PathVariable String id) {
        BookingDetailResponse response = bookingService.getBookingDetail(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Quản trị viên chủ động hủy đơn đặt vé chưa thanh toán (PENDING_PAYMENT) và giải phóng ghế.
     */
    @Operation(summary = "Hủy đơn đặt vé chưa thanh toán dành cho Quản trị viên")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingDetailResponse> cancelAdminBooking(
            @PathVariable String id,
            @Valid @RequestBody(required = false) CancelBookingRequest request
    ) {
        BookingDetailResponse response = bookingService.cancelBooking(id, request);
        return ResponseEntity.ok(response);
    }
}