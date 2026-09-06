package com.cinebook.controller;

import com.cinebook.config.VnPayConfig;
import com.cinebook.dto.request.DemoPaymentCompleteRequest;
import com.cinebook.dto.response.DemoPaymentCompleteResponse;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ForbiddenException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.exception.UnauthorizedException;
import com.cinebook.repository.PaymentRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.PaymentService;
import com.cinebook.service.VnPayService;
import com.cinebook.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Tag(name = "Demo Payment", description = "Endpoints for simulating VNPay payments locally in mock gateway mode")
@RestController
@RequestMapping("/api/v1/payments/demo")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "cinebook.payment.gateway", havingValue = "mock", matchIfMissing = false)
public class DemoPaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final VnPayService vnPayService;
    private final VnPayConfig vnPayConfig;
    private final SeatHoldRepository seatHoldRepository;

    @Operation(
            summary = "Mô phỏng hoàn tất thanh toán VNPay cục bộ (Chỉ khả dụng khi gateway=mock)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/complete")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<DemoPaymentCompleteResponse> completeDemoPayment(
            @Valid @RequestBody DemoPaymentCompleteRequest request
    ) {
        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        Payment payment = paymentRepository.findByPaymentCode(request.getPaymentCode())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán cho mã: " + request.getPaymentCode()));

        Booking booking = payment.getBooking();
        boolean isOwner = booking.getUser() != null && booking.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.isAdmin();

        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("Bạn không có quyền thao tác với thanh toán của đơn đặt vé này.");
        }

        // Idempotency: If already SUCCESS, return idempotent redirect URL immediately
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.info("[DEMO GATEWAY] Payment {} is already SUCCESS. Returning idempotent redirect URL.", payment.getPaymentCode());
            Map<String, String> signedParams = buildSignedParams(payment, "00");
            String redirectUrl = buildRedirectUrl(signedParams);
            return ResponseEntity.ok(DemoPaymentCompleteResponse.builder()
                    .paymentCode(payment.getPaymentCode())
                    .responseCode("00")
                    .paymentStatus(PaymentStatus.SUCCESS)
                    .redirectUrl(redirectUrl)
                    .message("Giao dịch thanh toán đã được xác nhận thành công trước đó.")
                    .build());
        }

        // If payment is terminal FAILED or CANCELLED, reject continuation
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Giao dịch thanh toán đang ở trạng thái " + payment.getPaymentStatus()
                    + ", không thể tiếp tục xử lý. Vui lòng tạo phiên thanh toán mới.");
        }

        // Validate booking status
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Đơn đặt vé đang ở trạng thái " + booking.getBookingStatus()
                    + ", không thể xác nhận thanh toán.");
        }

        // Validate hold expiration
        LocalDateTime now = LocalDateTime.now();
        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(now)) {
            throw new BadRequestException("Đơn đặt vé đã hết hạn giữ chỗ.");
        }

        if (seatHoldRepository.findByBookingId(booking.getId()).isEmpty()) {
            throw new BadRequestException("Không tìm thấy thông tin giữ chỗ cho đơn đặt vé này hoặc giữ chỗ đã hết hạn.");
        }

        // Construct authoritative VNPay parameters
        Map<String, String> signedParams = buildSignedParams(payment, request.getResponseCode());

        // Trigger real IPN processing
        IpnResponse ipnResponse = paymentService.processIpn(signedParams);
        log.info("[DEMO GATEWAY] Processed IPN for payment {} with responseCode {}: RspCode={}, Message={}",
                payment.getPaymentCode(), request.getResponseCode(), ipnResponse.getRspCode(), ipnResponse.getMessage());

        Payment updatedPayment = paymentRepository.findByPaymentCode(payment.getPaymentCode()).orElse(payment);
        String redirectUrl = buildRedirectUrl(signedParams);

        String message = "00".equals(request.getResponseCode())
                ? "Giao dịch thanh toán thành công."
                : ("24".equals(request.getResponseCode())
                ? "Khách hàng đã hủy giao dịch trên cổng thanh toán."
                : "Giao dịch không thành công do lỗi hệ thống ngân hàng.");

        return ResponseEntity.ok(DemoPaymentCompleteResponse.builder()
                .paymentCode(updatedPayment.getPaymentCode())
                .responseCode(request.getResponseCode())
                .paymentStatus(updatedPayment.getPaymentStatus())
                .redirectUrl(redirectUrl)
                .message(message)
                .build());
    }

    private Map<String, String> buildSignedParams(Payment payment, String responseCode) {
        Booking booking = payment.getBooking();
        long vnpAmount = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        String tmnCode = StringUtils.hasText(vnPayConfig.getTmnCode()) ? vnPayConfig.getTmnCode() : "MOCK_TMN";
        String txnNo = "MOCK-TXN-" + payment.getPaymentCode().replace("PAY-", "");
        String payDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnPayConfig.getVersion());
        vnpParams.put("vnp_Command", vnPayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", tmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", payment.getPaymentCode());
        vnpParams.put("vnp_OrderInfo", "Thanh toan ve xem phim " + booking.getBookingCode());
        vnpParams.put("vnp_ResponseCode", responseCode);
        vnpParams.put("vnp_TransactionStatus", "00".equals(responseCode) ? "00" : "02");
        vnpParams.put("vnp_TransactionNo", txnNo);
        vnpParams.put("vnp_BankCode", "NCB");
        vnpParams.put("vnp_CardType", "ATM");
        vnpParams.put("vnp_PayDate", payDate);

        String secretKey = StringUtils.hasText(vnPayConfig.getHashSecret()) ? vnPayConfig.getHashSecret() : "MOCK_SECRET_KEY";
        String secureHash = vnPayService.calculateHmacSha512(vnpParams, secretKey);
        vnpParams.put("vnp_SecureHash", secureHash);

        return vnpParams;
    }

    private String buildRedirectUrl(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder query = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = params.get(fieldName);
            if (StringUtils.hasText(fieldValue)) {
                try {
                    String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.UTF_8.toString());
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.UTF_8.toString());
                    query.append(encodedKey).append('=').append(encodedValue).append('&');
                } catch (Exception e) {
                    log.error("[DEMO GATEWAY] Error encoding field {}: {}", fieldName, e.getMessage());
                }
            }
        }

        if (query.length() > 0) {
            query.setLength(query.length() - 1);
        }

        return "/payment/result?" + query.toString();
    }
}
