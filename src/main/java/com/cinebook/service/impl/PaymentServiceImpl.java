package com.cinebook.service.impl;

import com.cinebook.config.VnPayConfig;
import com.cinebook.dto.request.InitiatePaymentRequest;
import com.cinebook.dto.response.InitiatePaymentResponse;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.dto.response.PaymentResultResponse;
import com.cinebook.dto.response.PaymentSummaryResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.entity.SeatHold;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.exception.AppException;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ForbiddenException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.exception.UnauthorizedException;
import com.cinebook.mapper.BookingMapper;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.PaymentRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.util.SecurityUtils;
import com.cinebook.service.BookingService;

import com.cinebook.service.PaymentService;
import com.cinebook.service.VnPayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final String CODE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_PREFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final VnPayConfig vnPayConfig;
    private final VnPayService vnPayService;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final BookingMapper bookingMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override

    @Transactional(rollbackFor = Exception.class)
    public InitiatePaymentResponse initiatePayment(
            String bookingId,
            InitiatePaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        if (request == null || request.getPaymentMethod() == null) {
            throw new BadRequestException("Phương thức thanh toán không được để trống.");
        }

        if (request.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new BadRequestException("Phương thức thanh toán " + request.getPaymentMethod() + " hiện chưa được hỗ trợ. V1 chỉ hỗ trợ VNPAY.");
        }

        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        // Acquire pessimistic row lock on Booking to prevent concurrent payment initiation
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với id: " + bookingId));

        validateBookingOwnershipOrAdmin(booking, currentUser);

        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Đơn đặt vé đang ở trạng thái " + booking.getBookingStatus() + ", không thể tạo thanh toán mới.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(now)) {
            throw new BadRequestException("Đơn đặt vé đã hết hạn giữ chỗ.");
        }

        List<SeatHold> holds = seatHoldRepository.findByBookingId(booking.getId());
        if (holds.isEmpty()) {
            throw new BadRequestException("Không tìm thấy thông tin giữ chỗ cho đơn đặt vé này hoặc giữ chỗ đã hết hạn.");
        }

        // Single active PENDING payment invariant
        boolean hasPendingPayment = paymentRepository.existsByBookingIdAndPaymentStatus(booking.getId(), PaymentStatus.PENDING);
        if (hasPendingPayment) {
            throw new ConflictException("Đơn đặt vé đang có một phiên thanh toán đang chờ xử lý. Vui lòng hoàn tất hoặc chờ giao dịch hết hạn.");
        }

        BigDecimal totalAmount = booking.getTotalAmount();
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Tổng tiền đơn đặt vé không hợp lệ.");
        }

        String paymentCode = generatePaymentCode();

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setBooking(booking);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentCode(paymentCode);
        payment.setAmount(totalAmount);
        payment.setPaymentStatus(PaymentStatus.PENDING);

        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        String clientIp = vnPayService.extractClientIp(httpRequest);
        String paymentUrl = vnPayService.buildPaymentUrl(savedPayment, booking, clientIp);

        return InitiatePaymentResponse.builder()
                .paymentId(savedPayment.getId())
                .paymentCode(savedPayment.getPaymentCode())
                .amount(savedPayment.getAmount())
                .paymentUrl(paymentUrl)
                .expiresAt(booking.getHoldExpiresAt())
                .build();
    }

    @Override
    public IpnResponse processIpn(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return new IpnResponse("97", "Invalid Checksum");
        }

        // 1. Signature Verification
        String vnpSecureHash = params.get("vnp_SecureHash");
        if (!vnPayService.verifySignature(params, vnpSecureHash)) {
            log.warn("VNPay IPN signature verification failed for params: {}", sanitizeParamsForLog(params));
            return new IpnResponse("97", "Invalid Checksum");
        }

        // 2. Terminal ID (TMN Code) Verification
        String incomingTmnCode = params.get("vnp_TmnCode");
        if (incomingTmnCode == null || !incomingTmnCode.equals(vnPayConfig.getTmnCode())) {
            log.warn("VNPay IPN invalid TMN code: received {}, configured {}", incomingTmnCode, vnPayConfig.getTmnCode());
            return new IpnResponse("01", "Order not Found");
        }

        // 3. Lookup Payment by vnp_TxnRef
        String paymentCode = params.get("vnp_TxnRef");
        if (!StringUtils.hasText(paymentCode)) {
            return new IpnResponse("01", "Order not Found");
        }

        Payment payment = paymentRepository.findByPaymentCode(paymentCode).orElse(null);
        if (payment == null) {
            log.warn("VNPay IPN payment code not found in system: {}", paymentCode);
            return new IpnResponse("01", "Order not Found");
        }

        // 4. Amount Verification
        String vnpAmountStr = params.get("vnp_Amount");
        if (!StringUtils.hasText(vnpAmountStr)) {
            return new IpnResponse("04", "Invalid Amount");
        }

        long incomingAmount;
        try {
            incomingAmount = Long.parseLong(vnpAmountStr);
        } catch (NumberFormatException e) {
            return new IpnResponse("04", "Invalid Amount");
        }

        long expectedAmount = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        if (incomingAmount != expectedAmount) {
            log.warn("VNPay IPN amount mismatch for payment {}: incoming={}, expected={}", paymentCode, incomingAmount, expectedAmount);
            return new IpnResponse("04", "Invalid Amount");
        }

        // 5. Idempotency Check
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            log.info("VNPay IPN payment {} already confirmed with status: {}", paymentCode, payment.getPaymentStatus());
            return new IpnResponse("02", "Order already confirmed");
        }

        // 6. State Transition
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String transactionNo = params.get("vnp_TransactionNo");
        String rawJson = convertMapToJson(params);

        if ("00".equals(responseCode) && ("00".equals(transactionStatus) || transactionStatus == null)) {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            payment.setGatewayTransactionId(transactionNo);
            payment.setGatewayResponse(rawJson);
            paymentRepository.saveAndFlush(payment);

            log.info("VNPay IPN payment {} marked SUCCESS. Confirming paid booking {}...", paymentCode, payment.getBooking().getId());

            try {
                bookingService.confirmPaidBooking(payment.getBooking().getId(), payment.getId());
            } catch (AppException ex) {
                log.error("CRITICAL FINANCIAL EXCEPTION: Payment {} succeeded on VNPay but Booking {} could not be confirmed: {}. Requires manual/Admin V2 reconciliation.",
                        payment.getPaymentCode(), payment.getBooking().getId(), ex.getMessage());
                // Invariant: Payment SUCCESS is retained in database for audit/reconciliation.
                // Booking remains EXPIRED, no tickets are issued, no double-sold seats.
            }
        } else if ("24".equals(responseCode)) {
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
            payment.setGatewayTransactionId(transactionNo);
            payment.setGatewayResponse(rawJson);
            paymentRepository.saveAndFlush(payment);
            log.info("VNPay IPN payment {} cancelled by customer.", paymentCode);
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setGatewayTransactionId(transactionNo);
            payment.setGatewayResponse(rawJson);
            paymentRepository.saveAndFlush(payment);
            log.info("VNPay IPN payment {} failed with response code {}.", paymentCode, responseCode);
        }

        return new IpnResponse("00", "Confirm Success");
    }

    @Override
    public PaymentResultResponse processReturn(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new BadRequestException("Tham số phản hồi từ VNPay không hợp lệ.");
        }

        String vnpSecureHash = params.get("vnp_SecureHash");
        if (!vnPayService.verifySignature(params, vnpSecureHash)) {
            throw new BadRequestException("Chữ ký phản hồi không hợp lệ.");
        }

        String paymentCode = params.get("vnp_TxnRef");
        if (!StringUtils.hasText(paymentCode)) {
            throw new BadRequestException("Thiếu mã giao dịch thanh toán (vnp_TxnRef).");
        }

        Payment payment = paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán cho mã: " + paymentCode));

        String responseCode = params.get("vnp_ResponseCode");
        String message = mapVnPayResponseCodeToMessage(responseCode);

        return PaymentResultResponse.builder()
                .paymentId(payment.getId())
                .bookingId(payment.getBooking().getId())
                .bookingCode(payment.getBooking().getBookingCode())
                .paymentCode(payment.getPaymentCode())
                .amount(payment.getAmount())
                .paymentStatus(payment.getPaymentStatus())
                .responseCode(responseCode)
                .message(message)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummaryResponse getPaymentDetail(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán với id: " + paymentId));

        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        validateBookingOwnershipOrAdmin(payment.getBooking(), currentUser);

        return bookingMapper.toPaymentSummaryResponse(payment);
    }

    private void validateBookingOwnershipOrAdmin(Booking booking, UserDetailsImpl currentUser) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && (booking.getUser() == null || !booking.getUser().getId().equals(currentUser.getId()))) {
            throw new ForbiddenException("Bạn không có quyền thao tác với thanh toán của đơn đặt vé này.");
        }
    }

    private String generatePaymentCode() {
        String datePrefix = LocalDate.now().format(DATE_PREFIX_FORMATTER);
        String code;
        int attempts = 0;
        do {
            if (attempts++ > 10) {
                code = "PAY-" + datePrefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                break;
            }
            StringBuilder randomPart = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                randomPart.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = "PAY-" + datePrefix + "-" + randomPart;
        } while (paymentRepository.existsByPaymentCode(code));

        return code;
    }

    private String convertMapToJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            log.error("Failed to convert VNPay response map to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private String mapVnPayResponseCodeToMessage(String responseCode) {
        if ("00".equals(responseCode)) {
            return "Giao dịch thanh toán thành công.";
        } else if ("07".equals(responseCode)) {
            return "Trừ tiền thành công nhưng giao dịch bị nghi ngờ gian lận.";
        } else if ("09".equals(responseCode)) {
            return "Thẻ/Tài khoản chưa đăng ký dịch vụ Internet Banking.";
        } else if ("10".equals(responseCode)) {
            return "Xác thực thông tin thẻ/tài khoản không chính xác quá 3 lần.";
        } else if ("11".equals(responseCode)) {
            return "Đã hết thời gian chờ thanh toán trên cổng VNPay.";
        } else if ("12".equals(responseCode)) {
            return "Thẻ hoặc tài khoản thanh toán đang bị khóa.";
        } else if ("24".equals(responseCode)) {
            return "Khách hàng đã hủy giao dịch trên cổng thanh toán.";
        } else if ("51".equals(responseCode)) {
            return "Số dư tài khoản không đủ để thực hiện giao dịch.";
        } else if ("65".equals(responseCode)) {
            return "Tài khoản đã vượt quá hạn mức giao dịch trong ngày.";
        } else if ("75".equals(responseCode)) {
            return "Ngân hàng thanh toán đang trong quá trình bảo trì.";
        } else {
            return "Giao dịch không thành công do lỗi hệ thống ngân hàng.";
        }
    }

    private Map<String, String> sanitizeParamsForLog(Map<String, String> params) {
        // Return shallow copy without sensitive items if needed
        return params;
    }
}
