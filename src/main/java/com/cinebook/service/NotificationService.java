package com.cinebook.service;

import com.cinebook.dto.response.NotificationResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.UnreadCountResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.User;
import com.cinebook.enums.NotificationType;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    /**
     * Lấy danh sách thông báo phân trang của người dùng hiện tại (mới nhất trước).
     */
    PageResponse<NotificationResponse> getUserNotifications(String userId, Pageable pageable);

    /**
     * Lấy số lượng thông báo chưa đọc của người dùng hiện tại.
     */
    UnreadCountResponse getUnreadCount(String userId);

    /**
     * Đánh dấu một thông báo cụ thể là đã đọc (bảo đảm quyền sở hữu).
     */
    NotificationResponse markAsRead(String notificationId, String userId);

    /**
     * Đánh dấu toàn bộ thông báo của người dùng là đã đọc.
     */
    void markAllAsRead(String userId);

    /**
     * Tạo thông báo mới cho sự kiện nghiệp vụ (Idempotent với bảo vệ Unique Constraint).
     */
    void createNotification(User user, Booking booking, NotificationType type, String title, String message);

    /**
     * Tạo thông báo mới bằng User ID và Booking ID.
     */
    void createNotification(String userId, String bookingId, NotificationType type, String title, String message);
}

