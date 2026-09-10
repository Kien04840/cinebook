package com.cinebook.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tham số tích hợp Cổng thanh toán VNPay (VNPay Sandbox Configuration).
 * Các thuộc tính được ánh xạ tự động từ file cấu hình (application.yml hoặc application-local.yml)
 * thông qua tiền tố "vnpay".
 * 
 * Các trường dữ liệu quan trọng:
 * - tmnCode: Mã định danh Terminal/Website do VNPay cấp (Terminal ID).
 * - hashSecret: Chuỗi khóa bí mật dùng để tạo và kiểm tra chữ ký HMAC-SHA512 (Secret Key).
 * - paymentUrl: Địa chỉ URL cổng thanh toán Sandbox mà khách hàng được chuyển hướng tới để thanh toán.
 * - apiUrl: Địa chỉ WebAPI của VNPay dùng cho các nghiệp vụ truy vấn giao dịch hoặc hoàn tiền (Refund).
 * - returnUrl: Địa chỉ Web URL mà VNPay sẽ redirect trình duyệt của khách hàng quay về sau khi hoàn tất thanh toán.
 * - version: Phiên bản API VNPay (chuẩn 2.1.0).
 * - command: Lệnh thanh toán ("pay").
 */
@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Getter
@Setter
public class VnPayConfig {

    private String tmnCode = "";
    private String hashSecret = "";
    private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private String returnUrl = "http://localhost:5173/payment/result";
    private String version = "2.1.0";
    private String command = "pay";
    private String orderType = "other";
}


