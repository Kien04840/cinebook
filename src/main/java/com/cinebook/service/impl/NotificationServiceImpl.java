package com.cinebook.service.impl;

import com.cinebook.dto.response.NotificationResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.UnreadCountResponse;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Notification;
import com.cinebook.entity.User;
import com.cinebook.enums.NotificationType;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.NotificationMapper;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.NotificationRepository;
import com.cinebook.repository.UserRepository;
import com.cinebook.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getUserNotifications(String userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(page, notificationMapper::toNotificationResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(String userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return new UnreadCountResponse(count);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationResponse markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Thông báo không tồn tại hoặc không thuộc quyền sở hữu."));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return notificationMapper.toNotificationResponse(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    /**
     * Tạo thông báo nghiệp vụ kèm bảo vệ Idempotent ở cả 2 cấp độ:
     * 1. Ứng dụng: existsByBookingIdAndType check trước khi lưu.
     * 2. Cơ sở dữ liệu: Unique constraint uk_notifications_booking_type(booking_id, type) ngăn chặn race condition.
     * 
     * Lưu ý quan trọng (Notification Failure Policy):
     * Phương thức chạy với Propagation.REQUIRES_NEW (hoặc try-catch độc lập) để lỗi lưu thông báo
     * không bao giờ làm rollback giao dịch tài chính chính (thanh toán/hủy/hoàn tiền).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(User user, Booking booking, NotificationType type, String title, String message) {
        if (user == null || booking == null || type == null) {
            log.warn("Bỏ qua tạo thông báo vì thiếu dữ liệu đầu vào: user={}, booking={}, type={}",
                    user != null ? user.getId() : null,
                    booking != null ? booking.getId() : null,
                    type);
            return;
        }

        // 1. Kiểm tra Idempotency mức ứng dụng
        if (notificationRepository.existsByBookingIdAndType(booking.getId(), type)) {
            log.info("Thông báo cho đơn đặt vé {} với loại {} đã tồn tại. Bỏ qua ghi đè trùng lặp.",
                    booking.getId(), type);
            return;
        }

        // 2. Lưu thông báo với bảo vệ Unique Constraint của Database
        try {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setBooking(booking);
            notification.setType(type);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setRead(false);

            Notification saved = notificationRepository.saveAndFlush(notification);
            log.info("Đã tạo thông báo {} (loại: {}, đơn vé: {}) cho người dùng {}",
                    saved.getId(), type, booking.getBookingCode(), user.getId());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Ngăn chặn tạo thông báo trùng lặp qua Unique Constraint DB cho đơn hàng {} (loại: {}): {}",
                    booking.getId(), type, ex.getMessage());
        } catch (Exception ex) {
            log.error("Lỗi khi tạo thông báo cho đơn hàng {} (loại: {}): {}",
                    booking.getId(), type, ex.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(String userId, String bookingId, NotificationType type, String title, String message) {
        if (userId == null || bookingId == null || type == null) {
            log.warn("Bỏ qua tạo thông báo vì thiếu ID đầu vào: userId={}, bookingId={}, type={}", userId, bookingId, type);
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        Booking booking = bookingRepository.findById(bookingId).orElse(null);

        if (user == null || booking == null) {
            log.warn("Không tìm thấy User ({}) hoặc Booking ({}) để gửi thông báo", userId, bookingId);
            return;
        }

        createNotification(user, booking, type, title, message);
    }
}

