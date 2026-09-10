package com.cinebook.enums;

/**
 * Phân loại các sự kiện thông báo trong hệ sinh thái CineBook.
 * Phase 3.2 MVP bao gồm 3 sự kiện nghiệp vụ cốt lõi gắn liền với đơn đặt vé:
 * - PAYMENT_SUCCESS: Thanh toán đơn vé thành công.
 * - BOOKING_CANCELLED: Hủy đơn đặt vé thành công.
 * - REFUND_COMPLETED: Hoàn tiền đơn vé thành công.
 */
public enum NotificationType {
    PAYMENT_SUCCESS,
    BOOKING_CANCELLED,
    REFUND_COMPLETED
}

