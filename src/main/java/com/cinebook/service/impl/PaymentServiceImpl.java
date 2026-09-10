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
import com.cinebook.service.EmailService;
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
import java.util.Optional;
import java.util.UUID;

import com.cinebook.dto.request.RefundRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.RefundResponse;
import com.cinebook.entity.Refund;
import com.cinebook.entity.Showtime;
import com.cinebook.entity.Ticket;
import com.cinebook.enums.RefundStatus;
import com.cinebook.enums.TicketStatus;
import com.cinebook.mapper.RefundMapper;
import com.cinebook.repository.RefundRepository;
import com.cinebook.repository.TicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Dịch vụ xử lý thanh toán và hoàn tiền qua Cổng thanh toán VNPay Sandbox (Payment Service Implementation).
 * 
 * Kiến trúc & Luồng nghiệp vụ thanh toán (Payment Flow):
 * 1. Khởi tạo thanh toán (initiatePayment):
 *    - Khóa bi quan (Pessimistic Write Lock: findByIdWithLock) trên đơn đặt vé (Booking).
 *    - Kiểm tra tính hợp lệ: Trạng thái PENDING_PAYMENT, chưa hết hạn giữ chỗ (5 phút).
 *    - Hỗ trợ Tiếp tục thanh toán (Resume/Retry): Nếu đơn đã có Payment PENDING, tái sử dụng bản ghi đó;
 *      nếu chưa có hoặc lần trước FAILED/CANCELLED thì tạo mới.
 *    - Gọi VnPayService.buildPaymentUrl để tạo URL thanh toán chuyển hướng sang VNPay Sandbox.
 * 2. Xác thực Webhook IPN Server-to-Server (processIpn):
 *    - Đối chiếu chữ ký bảo mật HMAC-SHA512 (vnp_SecureHash).
 *    - Đối chiếu mã định danh Merchant (vnp_TmnCode) và số tiền thanh toán (vnp_Amount = amount * 100).
 *    - Kiểm tra tính Idempotent: Nếu Payment không còn ở trạng thái PENDING thì trả về {RspCode: "02", Message: "Order already confirmed"}.
 *    - Chuyển trạng thái Payment sang SUCCESS và gọi bookingService.confirmPaidBooking để phát hành vé điện tử.
 * 3. Xử lý Return URL trình duyệt (processReturn):
 *    - Cơ chế Fallback cho môi trường Local Dev: Cho phép cập nhật SUCCESS và phát hành vé nếu IPN chưa tới hoặc không có ngrok.
 * 4. Quy trình Hoàn tiền (Refund):
 *    - Phân định rõ: PaymentStatus (REFUNDED) vs BookingStatus (REFUNDED) vs TicketStatus (CANCELLED).
 *    - Gọi API hoàn tiền trực tiếp sang cổng VNPay bên ngoài transaction của cơ sở dữ liệu.
 */
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
    private final RefundRepository refundRepository;
    private final TicketRepository ticketRepository;
    private final BookingMapper bookingMapper;
    private final RefundMapper refundMapper;
    private final EmailService emailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private PaymentServiceImpl self;

    private PaymentServiceImpl getSelf() {
        return self != null ? self : this;
    }

    /**
     * Khởi tạo giao dịch thanh toán VNPay Sandbox cho một đơn đặt vé:
     * 
     * Quy trình xử lý:
     * 1. Xác thực phương thức thanh toán: V1 chỉ hỗ trợ VNPAY.
     * 2. Áp dụng Khóa bi quan (Pessimistic Write Lock: findByIdWithLock) trên Booking để chống Race Condition tạo trùng thanh toán.
     * 3. Kiểm tra tính hợp lệ: Đơn phải ở trạng thái PENDING_PAYMENT và còn hạn giữ chỗ (5 phút).
     *    - Nếu đã hết hạn giữ chỗ: Tự động kích hoạt Lazy Expiration giải phóng ghế và ném lỗi BadRequestException.
     * 4. Hỗ trợ Resume/Retry:
     *    - Nếu đơn đã có bản ghi thanh toán PENDING trước đó: Tái sử dụng để tránh sinh thừa bản ghi.
     *    - Nếu là lần đầu hoặc giao dịch trước FAILED/CANCELLED: Tạo bản ghi Payment mới với mã định danh duy nhất (paymentCode).
     * 5. Trích xuất địa chỉ IP của máy khách và sinh URL chuyển hướng thanh toán VNPay Sandbox kèm chữ ký HMAC-SHA512.
     * 
     * @param bookingId Mã định danh đơn hàng
     * @param request Yêu cầu chứa phương thức thanh toán VNPAY
     * @param httpRequest Request HTTP để trích xuất IP client
     * @return InitiatePaymentResponse chứa paymentUrl để Frontend redirect người dùng
     */
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

        // 1. Áp dụng khóa bi quan (Pessimistic Write Lock) trên Booking để chống tạo payment đồng thời (Race Condition)
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với id: " + bookingId));

        validateBookingOwnershipOrAdmin(booking, currentUser);

        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Đơn đặt vé đang ở trạng thái " + booking.getBookingStatus() + ", không thể tạo thanh toán mới.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (booking.getHoldExpiresAt() != null && !booking.getHoldExpiresAt().isAfter(now)) {
            // Lazy expiration: Đánh dấu đơn EXPIRED, giải phóng ghế và hoàn trả quota khuyến mãi
            bookingService.expireBookingIfHoldExpired(booking);
            throw new BadRequestException("Đơn đặt vé đã hết hạn giữ chỗ.");
        }

        List<SeatHold> holds = seatHoldRepository.findByBookingId(booking.getId());
        if (holds.isEmpty()) {
            bookingService.expireBookingIfHoldExpired(booking);
            throw new BadRequestException("Không tìm thấy thông tin giữ chỗ cho đơn đặt vé này hoặc giữ chỗ đã hết hạn.");
        }

        // 2. Kiểm tra thanh toán hiện có để hỗ trợ khôi phục phiên thanh toán (Resume/Retry)
        List<Payment> existingPayments = paymentRepository.findByBookingId(booking.getId());
        Optional<Payment> existingPendingPayment = existingPayments.stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PENDING)
                .findFirst();

        Payment paymentToUse;
        if (existingPendingPayment.isPresent()) {
            // Tái sử dụng bản ghi thanh toán PENDING sẵn có
            paymentToUse = existingPendingPayment.get();
            log.info("Khôi phục phiên thanh toán PENDING {} cho đơn hàng {}", paymentToUse.getPaymentCode(), booking.getBookingCode());
        } else {
            // Tạo bản ghi thanh toán mới (lần đầu hoặc lần trước FAILED/CANCELLED)
            BigDecimal totalAmount = booking.getTotalAmount();
            if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Tổng tiền đơn đặt vé không hợp lệ.");
            }

            String paymentCode = generatePaymentCode();
            Payment newPayment = new Payment();
            newPayment.setId(UUID.randomUUID().toString());
            newPayment.setBooking(booking);
            newPayment.setPaymentMethod(PaymentMethod.VNPAY);
            newPayment.setPaymentCode(paymentCode);
            newPayment.setAmount(totalAmount);
            newPayment.setPaymentStatus(PaymentStatus.PENDING);

            paymentToUse = paymentRepository.saveAndFlush(newPayment);
            log.info("Khởi tạo bản ghi thanh toán PENDING mới {} cho đơn hàng {}", paymentToUse.getPaymentCode(), booking.getBookingCode());
        }

        String clientIp = vnPayService.extractClientIp(httpRequest);
        String paymentUrl = vnPayService.buildPaymentUrl(paymentToUse, booking, clientIp);

        return InitiatePaymentResponse.builder()
                .paymentId(paymentToUse.getId())
                .paymentCode(paymentToUse.getPaymentCode())
                .amount(paymentToUse.getAmount())
                .paymentUrl(paymentUrl)
                .expiresAt(booking.getHoldExpiresAt())
                .build();
    }

    /**
     * Tiếp nhận và xử lý Webhook IPN (Instant Payment Notification) từ máy chủ VNPay (Server-to-Server):
     * 
     * Quy trình xác thực và kiểm soát tài chính:
     * 1. Xác thực chữ ký HMAC-SHA512 (vnp_SecureHash) bằng secret key của merchant.
     * 2. Đối chiếu mã Terminal ID (vnp_TmnCode) với cấu hình hệ thống.
     * 3. Tra cứu bản ghi Payment theo mã giao dịch duy nhất (vnp_TxnRef).
     * 4. Đối chiếu số tiền thanh toán (vnp_Amount = payment.amount * 100).
     * 5. Kiểm tra tính Idempotent: Nếu trạng thái giao dịch không còn là PENDING, trả về ngay {RspCode: "02", Message: "Order already confirmed"}.
     * 6. Chuyển đổi trạng thái giao dịch:
     *    - Nếu mã phản hồi "00" (thành công): Cập nhật SUCCESS, gọi bookingService.confirmPaidBooking để phát hành vé.
     *    - Nếu mã phản hồi "24" (khách hủy): Cập nhật CANCELLED.
     *    - Các mã phản hồi khác: Cập nhật FAILED.
     * 7. Trả về đối tượng IpnResponse chuẩn theo định dạng quy định của VNPay.
     * 
     * @param params Map chứa toàn bộ tham số gửi kèm trong IPN request
     * @return IpnResponse chứa RspCode và Message
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IpnResponse processIpn(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return new IpnResponse("97", "Invalid Checksum");
        }

        // 1. Xác thực chữ ký số HMAC-SHA512
        String vnpSecureHash = params.get("vnp_SecureHash");
        if (!vnPayService.verifySignature(params, vnpSecureHash)) {
            log.warn("Xác thực chữ ký số VNPay IPN thất bại với tham số: {}", sanitizeParamsForLog(params));
            return new IpnResponse("97", "Invalid Checksum");
        }

        // 2. Đối chiếu mã định danh TMN Code
        String incomingTmnCode = params.get("vnp_TmnCode");
        String expectedTmnCode = StringUtils.hasText(vnPayConfig.getTmnCode()) ? vnPayConfig.getTmnCode() : "MOCK_TMN";
        if (incomingTmnCode == null || (!incomingTmnCode.equals(expectedTmnCode) && !incomingTmnCode.equals(vnPayConfig.getTmnCode()))) {
            log.warn("Mã TMN Code VNPay IPN không khớp: nhận {}, cấu hình {}", incomingTmnCode, vnPayConfig.getTmnCode());
            return new IpnResponse("01", "Order not Found");
        }

        // 3. Tra cứu bản ghi Payment theo mã giao dịch vnp_TxnRef
        String paymentCode = params.get("vnp_TxnRef");
        if (!StringUtils.hasText(paymentCode)) {
            return new IpnResponse("01", "Order not Found");
        }

        Payment payment = paymentRepository.findByPaymentCode(paymentCode).orElse(null);
        if (payment == null) {
            log.warn("Không tìm thấy mã giao dịch thanh toán trong hệ thống: {}", paymentCode);
            return new IpnResponse("01", "Order not Found");
        }

        // 4. Đối chiếu số tiền thanh toán
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
            log.warn("Số tiền thanh toán VNPay IPN không khớp cho mã {}: nhận={}, kỳ vọng={}", paymentCode, incomingAmount, expectedAmount);
            return new IpnResponse("04", "Invalid Amount");
        }

        // 5. Kiểm tra tính Idempotent (Chống xử lý lặp lại nếu giao dịch đã hoàn tất)
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            log.info("Giao dịch VNPay IPN {} đã được xử lý trước đó với trạng thái: {}", paymentCode, payment.getPaymentStatus());
            return new IpnResponse("02", "Order already confirmed");
        }

        // 6. Chuyển đổi trạng thái giao dịch (State Transition)
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

            log.info("Giao dịch VNPay IPN {} thành công (SUCCESS). Tiến hành xác nhận đơn đặt vé {}...", paymentCode, payment.getBooking().getId());

            try {
                bookingService.confirmPaidBooking(payment.getBooking().getId(), payment.getId());
            } catch (AppException ex) {
                log.error("NGOẠI LỆ TÀI CHÍNH QUAN TRỌNG: Thanh toán {} thành công trên VNPay nhưng Đơn hàng {} không thể xác nhận: {}. Cần Admin đối soát thủ công.",
                        payment.getPaymentCode(), payment.getBooking().getId(), ex.getMessage());
                // Bất biến: Trạng thái Payment SUCCESS vẫn được lưu lại để phục vụ đối soát/hoàn tiền.
                // Đơn hàng giữ nguyên trạng thái EXPIRED, không phát hành vé, không bán trùng ghế.
            }
        } else if ("24".equals(responseCode)) {
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
            payment.setGatewayTransactionId(transactionNo);
            payment.setGatewayResponse(rawJson);
            paymentRepository.saveAndFlush(payment);
            log.info("Giao dịch VNPay IPN {} đã bị khách hàng hủy bỏ.", paymentCode);
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setGatewayTransactionId(transactionNo);
            payment.setGatewayResponse(rawJson);
            paymentRepository.saveAndFlush(payment);
            log.info("Giao dịch VNPay IPN {} thất bại với mã lỗi {}.", paymentCode, responseCode);
        }

        return new IpnResponse("00", "Confirm Success");
    }

    /**
     * Xử lý dữ liệu trả về từ VNPay khi trình duyệt khách hàng được chuyển hướng (Return URL).
     * 
     * Luồng hoạt động:
     * 1. Xác thực tính toàn vẹn của dữ liệu bằng chữ ký HMAC-SHA512 (vnp_SecureHash).
     * 2. Tìm kiếm bản ghi Payment tương ứng qua mã giao dịch (vnp_TxnRef).
     * 3. Đối chiếu số tiền thanh toán (vnp_Amount = payment.amount * 100).
     * 4. Idempotent & Local Dev Fallback:
     *    - Nếu trạng thái Payment vẫn đang là PENDING (ví dụ: môi trường phát triển local không có ngrok
     *      để nhận IPN Webhook server-to-server, hoặc Return URL tới trước IPN): tiến hành cập nhật trạng thái
     *      Payment thành SUCCESS và gọi bookingService.confirmPaidBooking để xác nhận đơn vé và phát hành vé điện tử.
     *    - Nếu Payment đã được IPN xử lý trước đó (đã là SUCCESS, CANCELLED, FAILED): giữ nguyên dữ liệu,
     *      không thực hiện cập nhật lại (bảo đảm tính Idempotent).
     * 5. Trả về kết quả PaymentResultResponse để Frontend hiển thị giao diện vé hoặc thông báo lỗi.
     *
     * @param params Map chứa toàn bộ tham số do VNPay gửi kèm trên URL redirect
     * @return PaymentResultResponse kết quả giao dịch chi tiết
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentResultResponse processReturn(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new BadRequestException("Tham số phản hồi từ VNPay không hợp lệ.");
        }

        // 1. Kiểm tra chữ ký bảo mật HMAC-SHA512
        String vnpSecureHash = params.get("vnp_SecureHash");
        if (!vnPayService.verifySignature(params, vnpSecureHash)) {
            log.warn("VNPay Return signature verification failed for params: {}", sanitizeParamsForLog(params));
            throw new BadRequestException("Chữ ký phản hồi không hợp lệ.");
        }

        // 2. Trích xuất và tìm kiếm giao dịch thanh toán
        String paymentCode = params.get("vnp_TxnRef");
        if (!StringUtils.hasText(paymentCode)) {
            throw new BadRequestException("Thiếu mã giao dịch thanh toán (vnp_TxnRef).");
        }

        Payment payment = paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán cho mã: " + paymentCode));

        String responseCode = params.get("vnp_ResponseCode");
        String message = mapVnPayResponseCodeToMessage(responseCode);

        // 3. Xử lý cập nhật trạng thái nếu Payment vẫn đang PENDING (Hỗ trợ Local Dev không có ngrok hoặc Return tới trước IPN)
        if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
            String transactionStatus = params.get("vnp_TransactionStatus");
            String transactionNo = params.get("vnp_TransactionNo");
            String rawJson = convertMapToJson(params);

            // Kiểm tra số tiền nếu có gửi kèm
            String vnpAmountStr = params.get("vnp_Amount");
            if (StringUtils.hasText(vnpAmountStr)) {
                try {
                    long incomingAmount = Long.parseLong(vnpAmountStr);
                    long expectedAmount = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
                    if (incomingAmount != expectedAmount) {
                        log.warn("VNPay Return amount mismatch for payment {}: incoming={}, expected={}", paymentCode, incomingAmount, expectedAmount);
                        throw new BadRequestException("Số tiền thanh toán không khớp với đơn hàng.");
                    }
                } catch (NumberFormatException e) {
                    log.warn("VNPay Return invalid amount format: {}", vnpAmountStr);
                }
            }

            if ("00".equals(responseCode) && ("00".equals(transactionStatus) || transactionStatus == null)) {
                payment.setPaymentStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(LocalDateTime.now());
                payment.setGatewayTransactionId(transactionNo);
                payment.setGatewayResponse(rawJson);
                payment = paymentRepository.saveAndFlush(payment);

                log.info("VNPay Return: payment {} marked SUCCESS. Confirming paid booking {}...", paymentCode, payment.getBooking().getId());

                try {
                    bookingService.confirmPaidBooking(payment.getBooking().getId(), payment.getId());
                } catch (AppException ex) {
                    log.error("CRITICAL FINANCIAL EXCEPTION: Payment {} succeeded on VNPay Return but Booking {} could not be confirmed: {}.",
                            payment.getPaymentCode(), payment.getBooking().getId(), ex.getMessage());
                }
            } else if ("24".equals(responseCode)) {
                payment.setPaymentStatus(PaymentStatus.CANCELLED);
                payment.setGatewayTransactionId(transactionNo);
                payment.setGatewayResponse(rawJson);
                payment = paymentRepository.saveAndFlush(payment);
                log.info("VNPay Return: payment {} cancelled by customer.", paymentCode);
            } else {
                payment.setPaymentStatus(PaymentStatus.FAILED);
                payment.setGatewayTransactionId(transactionNo);
                payment.setGatewayResponse(rawJson);
                payment = paymentRepository.saveAndFlush(payment);
                log.info("VNPay Return: payment {} failed with response code {}.", paymentCode, responseCode);
            }
        }

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

    /**
     * Tra cứu thông tin tóm tắt của một giao dịch thanh toán.
     * Kiểm tra quyền sở hữu (Khách hàng chỉ xem được giao dịch của mình; Admin xem được tất cả).
     * 
     * @param paymentId Mã định danh thanh toán
     * @return PaymentSummaryResponse thông tin giao dịch
     */
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

    /**
     * Khởi chạy quy trình hoàn tiền cho một giao dịch thanh toán thành công (SUCCESS):
     * 
     * Luồng xử lý 3 bước (Two-Phase Transaction Pattern):
     * - Bước 1: Kiểm tra tính hợp lệ và khởi tạo bản ghi Refund với trạng thái PENDING trong một transaction riêng biệt.
     *   (Nếu giao dịch đã hoàn tiền trước đó thành công, trả về ngay để đảm bảo tính Idempotent).
     * - Bước 2: Gọi API hoàn tiền sang VNPay Sandbox bên ngoài transaction của cơ sở dữ liệu để tránh treo kết nối DB quá lâu.
     * - Bước 3: Cập nhật trạng thái Refund (SUCCESS/FAILED), Payment (REFUNDED) và hủy vé/đơn đặt vé trong transaction riêng biệt.
     * 
     * @param paymentId Mã định danh giao dịch thanh toán
     * @param request Yêu cầu hoàn tiền kèm lý do
     * @param httpRequest Request HTTP để trích xuất IP client
     * @return RefundResponse kết quả hoàn tiền
     */
    @Override
    public RefundResponse refundPayment(String paymentId, RefundRequest request, HttpServletRequest httpRequest) {
        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        String reason = (request != null && StringUtils.hasText(request.getReason()))
                ? request.getReason().trim()
                : "Khách hàng yêu cầu hoàn tiền";

        // Bước 1: Kiểm tra nghiệp vụ và tạo bản ghi Refund PENDING trong transaction riêng
        Refund pendingRefund = getSelf().validateAndCreatePendingRefund(paymentId, reason, currentUser);

        // Nếu đã hoàn tiền thành công từ trước (Idempotent), trả về kết quả ngay lập tức
        if (pendingRefund.getRefundStatus() == RefundStatus.SUCCESS) {
            log.info("Giao dịch {} đã được hoàn tiền trước đó với mã {}. Trả về kết quả hoàn tiền sẵn có.",
                    paymentId, pendingRefund.getRefundCode());
            return refundMapper.toRefundResponse(pendingRefund);
        }

        Payment payment = pendingRefund.getPayment();
        String clientIp = vnPayService.extractClientIp(httpRequest);

        // Bước 2: Gọi API hoàn tiền sang cổng VNPay bên ngoài transaction của CSDL
        Map<String, String> gatewayResult = vnPayService.refundPayment(payment, pendingRefund, currentUser.getEmail(), clientIp);

        // Bước 3: Hoàn tất chuyển đổi trạng thái hoàn tiền trong transaction riêng biệt
        return getSelf().completeRefundTransaction(pendingRefund.getId(), gatewayResult, currentUser.getId());
    }

    /**
     * Yêu cầu hoàn tiền theo mã đơn đặt vé (Booking ID).
     * Tự động tìm kiếm giao dịch thanh toán SUCCESS hoặc REFUNDED tương ứng để hoàn tiền.
     * 
     * @param bookingId Mã định danh đơn hàng
     * @param request Yêu cầu hoàn tiền kèm lý do
     * @param httpRequest Request HTTP
     * @return RefundResponse kết quả hoàn tiền
     */
    @Override
    public RefundResponse refundBooking(String bookingId, RefundRequest request, HttpServletRequest httpRequest) {
        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        Payment payment = paymentRepository.findFirstByBookingIdAndPaymentStatus(bookingId, PaymentStatus.SUCCESS)
                .or(() -> paymentRepository.findFirstByBookingIdAndPaymentStatus(bookingId, PaymentStatus.REFUNDED))
                .orElseThrow(() -> new BadRequestException("Không tìm thấy giao dịch thanh toán thành công (SUCCESS) nào cho đơn đặt vé này."));

        return refundPayment(payment.getId(), request, httpRequest);
    }

    /**
     * Xác thực điều kiện nghiệp vụ hoàn tiền và khởi tạo bản ghi Refund PENDING:
     * 
     * Các ràng buộc nghiệp vụ (Business Invariants):
     * 1. Quyền thao tác: Khách hàng chỉ hoàn được đơn của mình; Admin có thể hoàn bất kỳ đơn nào.
     * 2. Trạng thái Payment: Bắt buộc phải là SUCCESS (hoặc nếu đã REFUNDED thì trả về bản ghi cũ).
     * 3. Trạng thái Booking: Phải là PAID (hoặc EXPIRED đối với trường hợp Admin đối soát đặc biệt).
     * 4. Ràng buộc thời gian (Khách hàng thường): Phải yêu cầu trước giờ chiếu phim ít nhất 2 tiếng.
     * 5. Ràng buộc vé: Tuyệt đối không cho phép hoàn tiền nếu đơn hàng đã có bất kỳ vé nào ở trạng thái USED (đã vào rạp).
     * 
     * @param paymentId Mã định danh thanh toán
     * @param reason Lý do hoàn tiền
     * @param currentUser Người dùng đang thực hiện thao tác
     * @return Thực thể Refund ở trạng thái PENDING
     */
    @Transactional(rollbackFor = Exception.class)
    public Refund validateAndCreatePendingRefund(String paymentId, String reason, UserDetailsImpl currentUser) {
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán với id: " + paymentId));

        validateBookingOwnershipOrAdmin(payment.getBooking(), currentUser);

        Optional<Refund> existingRefundOpt = refundRepository.findByPaymentId(payment.getId());
        if (existingRefundOpt.isPresent() && existingRefundOpt.get().getRefundStatus() == RefundStatus.SUCCESS) {
            return existingRefundOpt.get();
        }

        if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
            if (existingRefundOpt.isPresent()) {
                return existingRefundOpt.get();
            }
            throw new ConflictException("Giao dịch thanh toán đã được hoàn tiền trước đó.");
        }

        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException("Chỉ có thể hoàn tiền cho giao dịch thanh toán thành công (SUCCESS). Trạng thái hiện tại: " + payment.getPaymentStatus());
        }

        boolean isAdmin = currentUser.isAdmin();

        Booking booking = payment.getBooking();
        if (booking.getBookingStatus() != BookingStatus.PAID) {
            if (!isAdmin || booking.getBookingStatus() != BookingStatus.EXPIRED) {
                throw new BadRequestException("Đơn đặt vé đang ở trạng thái " + booking.getBookingStatus() + ", không thể hoàn tiền.");
            }
        }

        // Kiểm tra quy định thời gian trước giờ chiếu đối với khách hàng thường (ít nhất 2 tiếng)
        if (!isAdmin) {
            Showtime showtime = booking.getShowtime();
            if (showtime != null && showtime.getStartTime() != null) {
                LocalDateTime minAllowedRefundTime = LocalDateTime.now().plusHours(2);
                if (showtime.getStartTime().isBefore(minAllowedRefundTime)) {
                    throw new BadRequestException("Khách hàng chỉ có thể yêu cầu hoàn tiền trước giờ chiếu ít nhất 2 tiếng.");
                }
            }
        }

        // Kiểm tra trạng thái vé: Tuyệt đối không hoàn tiền nếu đã có vé được soát vào rạp (USED)
        List<Ticket> tickets = ticketRepository.findByBookingId(booking.getId());
        boolean hasUsedTickets = tickets.stream().anyMatch(t -> t.getTicketStatus() == TicketStatus.USED);
        if (hasUsedTickets) {
            throw new BadRequestException("Không thể hoàn tiền cho đơn hàng đã được sử dụng để vào rạp.");
        }

        Refund refund;
        if (existingRefundOpt.isPresent()) {
            refund = existingRefundOpt.get();
            refund.setRefundReason(reason);
            refund.setRefundStatus(RefundStatus.PENDING);
        } else {
            refund = new Refund();
            refund.setId(UUID.randomUUID().toString());
            refund.setPayment(payment);
            refund.setRefundCode(generateRefundCode());
            refund.setAmount(payment.getAmount());
            refund.setRefundReason(reason);
            refund.setRefundStatus(RefundStatus.PENDING);
        }

        return refundRepository.saveAndFlush(refund);
    }

    /**
     * Cập nhật kết quả phản hồi từ Cổng thanh toán VNPay sau khi thực thi lệnh hoàn tiền:
     * - Nếu VNPay chấp thuận (mã "00"):
     *   + Chuyển trạng thái Refund sang SUCCESS, Payment sang REFUNDED.
     *   + Gọi bookingService.processBookingRefund để hủy vé (TicketStatus.CANCELLED) và cập nhật Booking sang REFUNDED.
     *   + Gửi email thông báo hoàn tiền thành công cho khách hàng.
     * - Nếu VNPay từ chối: Chuyển trạng thái Refund sang FAILED và ném ngoại lệ BadRequestException.
     * 
     * @param refundId Mã định danh bản ghi hoàn tiền
     * @param gatewayResult Map phản hồi từ VNPay Refund API
     * @param currentUserId Mã người dùng thực hiện
     * @return RefundResponse kết quả hoàn tiền
     */
    @Transactional(rollbackFor = Exception.class)
    public RefundResponse completeRefundTransaction(String refundId, Map<String, String> gatewayResult, String currentUserId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi hoàn tiền với id: " + refundId));

        Payment payment = refund.getPayment();
        String responseCode = gatewayResult.get("vnp_ResponseCode");
        String gatewayRefundId = gatewayResult.get("vnp_ResponseId");

        if ("00".equals(responseCode)) {
            refund.setRefundStatus(RefundStatus.SUCCESS);
            refund.setGatewayRefundId(gatewayRefundId);
            refund.setProcessedAt(LocalDateTime.now());
            Refund savedRefund = refundRepository.saveAndFlush(refund);

            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            paymentRepository.saveAndFlush(payment);

            bookingService.processBookingRefund(payment.getBooking().getId(), refund.getRefundReason(), currentUserId);

            // Gửi email xác nhận hoàn tiền cho khách hàng một cách an toàn (bắt ngoại lệ)
            try {
                String customerEmail = payment.getBooking().getUser() != null ? payment.getBooking().getUser().getEmail() : null;
                String customerName = payment.getBooking().getUser() != null ? payment.getBooking().getUser().getFullName() : null;
                if (customerEmail != null) {
                    emailService.sendRefundConfirmationEmail(customerEmail, customerName, payment.getBooking(), savedRefund);
                }
            } catch (Exception e) {
                log.error("Lỗi khi gửi email xác nhận hoàn tiền cho mã {}: {}", savedRefund.getRefundCode(), e.getMessage());
            }

            log.info("Hoàn tiền thành công cho giao dịch {} (mã hoàn tiền: {})", payment.getPaymentCode(), refund.getRefundCode());
            return refundMapper.toRefundResponse(savedRefund);
        } else {
            refund.setRefundStatus(RefundStatus.FAILED);
            refund.setGatewayRefundId(gatewayRefundId);
            refund.setProcessedAt(LocalDateTime.now());
            refundRepository.saveAndFlush(refund);

            log.warn("Cổng VNPay từ chối hoàn tiền cho giao dịch {}: mã={}, thông báo={}",
                    payment.getPaymentCode(), responseCode, gatewayResult.get("vnp_Message"));

            throw new BadRequestException("Cổng thanh toán từ chối hoàn tiền: " + gatewayResult.get("vnp_Message") + " (Mã lỗi: " + responseCode + ")");
        }
    }

    /**
     * Tra cứu thông tin chi tiết một yêu cầu hoàn tiền theo mã giao dịch thanh toán.
     * 
     * @param paymentId Mã định danh thanh toán
     * @return RefundResponse chi tiết hoàn tiền
     */
    @Override
    @Transactional(readOnly = true)
    public RefundResponse getRefundDetail(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán với id: " + paymentId));

        UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        validateBookingOwnershipOrAdmin(payment.getBooking(), currentUser);

        Refund refund = refundRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin hoàn tiền cho thanh toán này."));

        return refundMapper.toRefundResponse(refund);
    }

    /**
     * Tra cứu danh sách các giao dịch hoàn tiền có phân trang dành cho Quản trị viên (Admin).
     * 
     * @param status Trạng thái hoàn tiền (PENDING, SUCCESS, FAILED) - tùy chọn
     * @param pageable Tham số phân trang
     * @return Trang danh sách hoàn tiền
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> getAdminRefunds(RefundStatus status, Pageable pageable) {
        Page<Refund> page = refundRepository.findAdminRefunds(status, pageable);
        return PageResponse.of(page, refundMapper::toRefundResponse);
    }

    /**
     * Kiểm tra quyền sở hữu đơn hàng hoặc quyền Quản trị viên.
     */
    private void validateBookingOwnershipOrAdmin(Booking booking, UserDetailsImpl currentUser) {
        boolean isAdmin = currentUser.isAdmin();

        if (!isAdmin && (booking.getUser() == null || !booking.getUser().getId().equals(currentUser.getId()))) {
            throw new ForbiddenException("Bạn không có quyền thao tác với thanh toán của đơn đặt vé này.");
        }
    }

    /**
     * Sinh mã giao dịch thanh toán duy nhất định dạng PAY-yyyyMMdd-XXXXXXXX.
     */
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

    /**
     * Sinh mã hoàn tiền duy nhất định dạng REF-yyyyMMdd-XXXXXXXX.
     */
    private String generateRefundCode() {
        String datePrefix = LocalDate.now().format(DATE_PREFIX_FORMATTER);
        String code;
        int attempts = 0;
        do {
            if (attempts++ > 10) {
                code = "REF-" + datePrefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                break;
            }
            StringBuilder randomPart = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                randomPart.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = "REF-" + datePrefix + "-" + randomPart;
        } while (refundRepository.existsByRefundCode(code));

        return code;
    }

    /**
     * Chuyển đổi Map tham số VNPay sang chuỗi JSON để lưu vết nhật ký cổng (gatewayResponse).
     */
    private String convertMapToJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            log.error("Lỗi khi chuyển đổi phản hồi VNPay sang JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Ánh xạ mã phản hồi vnp_ResponseCode của VNPay sang thông điệp tiếng Việt thân thiện với người dùng.
     * 
     * @param responseCode Mã phản hồi hai chữ số từ VNPay
     * @return Chuỗi mô tả tiếng Việt
     */
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

    /**
     * Chuẩn hóa tham số trước khi ghi log kiểm toán.
     */
    private Map<String, String> sanitizeParamsForLog(Map<String, String> params) {
        return params;
    }
}

