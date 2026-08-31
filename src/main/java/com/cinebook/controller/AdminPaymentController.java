package com.cinebook.controller;

import com.cinebook.dto.request.RefundRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.PaymentSummaryResponse;
import com.cinebook.dto.response.RefundResponse;
import com.cinebook.enums.RefundStatus;
import com.cinebook.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Payment & Refund", description = "Admin payment management, reconciliation, and refund operations")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Admin hoàn tiền cho đơn đặt vé (kể cả trường hợp thanh toán thành công nhưng đơn bị EXPIRED)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/bookings/{bookingId}/refund")
    public ResponseEntity<RefundResponse> refundBooking(
            @PathVariable String bookingId,
            @Valid @RequestBody(required = false) RefundRequest request,
            HttpServletRequest httpRequest
    ) {
        RefundResponse response = paymentService.refundBooking(bookingId, request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Admin xem danh sách các giao dịch hoàn tiền",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/refunds")
    public ResponseEntity<PageResponse<RefundResponse>> getAdminRefunds(
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<RefundResponse> response = paymentService.getAdminRefunds(status, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Admin xem chi tiết một giao dịch thanh toán",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/payments/{id}")
    public ResponseEntity<PaymentSummaryResponse> getAdminPaymentDetail(@PathVariable String id) {
        PaymentSummaryResponse response = paymentService.getPaymentDetail(id);
        return ResponseEntity.ok(response);
    }
}

