package com.cinebook.controller;

import com.cinebook.dto.request.InitiatePaymentRequest;
import com.cinebook.dto.request.RefundRequest;
import com.cinebook.dto.response.InitiatePaymentResponse;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.dto.response.PaymentResultResponse;
import com.cinebook.dto.response.PaymentSummaryResponse;
import com.cinebook.dto.response.RefundResponse;
import com.cinebook.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Payment", description = "Payment management and VNPay Sandbox integration endpoints")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Khởi tạo phiên thanh toán VNPay cho đơn đặt vé",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/api/v1/bookings/{bookingId}/payments")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<InitiatePaymentResponse> initiatePayment(
            @PathVariable String bookingId,
            @Valid @RequestBody InitiatePaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        InitiatePaymentResponse response = paymentService.initiatePayment(bookingId, request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "VNPay IPN Webhook (Server-to-Server) - GET")
    @GetMapping("/api/v1/payments/vnpay/ipn")
    public ResponseEntity<IpnResponse> processIpnGet(@RequestParam Map<String, String> params) {
        IpnResponse response = paymentService.processIpn(params);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "VNPay IPN Webhook (Server-to-Server) - POST")
    @PostMapping("/api/v1/payments/vnpay/ipn")
    public ResponseEntity<IpnResponse> processIpnPost(@RequestParam Map<String, String> params) {
        IpnResponse response = paymentService.processIpn(params);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "VNPay Return URL (User browser redirect) - GET")
    @GetMapping("/api/v1/payments/vnpay/return")
    public ResponseEntity<PaymentResultResponse> processReturnGet(@RequestParam Map<String, String> params) {
        PaymentResultResponse response = paymentService.processReturn(params);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "VNPay Return URL (User browser redirect) - POST")
    @PostMapping("/api/v1/payments/vnpay/return")
    public ResponseEntity<PaymentResultResponse> processReturnPost(@RequestParam Map<String, String> params) {
        PaymentResultResponse response = paymentService.processReturn(params);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Lấy thông tin chi tiết của một giao dịch thanh toán",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/api/v1/payments/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<PaymentSummaryResponse> getPaymentDetail(@PathVariable String id) {
        PaymentSummaryResponse response = paymentService.getPaymentDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Yêu cầu hoàn tiền cho giao dịch thanh toán",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/api/v1/payments/{paymentId}/refund")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<RefundResponse> refundPayment(
            @PathVariable String paymentId,
            @Valid @RequestBody(required = false) RefundRequest request,
            HttpServletRequest httpRequest
    ) {
        RefundResponse response = paymentService.refundPayment(paymentId, request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Lấy thông tin hoàn tiền của một giao dịch thanh toán",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/api/v1/payments/{paymentId}/refund")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<RefundResponse> getRefundDetail(@PathVariable String paymentId) {
        RefundResponse response = paymentService.getRefundDetail(paymentId);
        return ResponseEntity.ok(response);
    }
}


