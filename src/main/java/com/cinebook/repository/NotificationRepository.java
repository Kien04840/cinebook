package com.cinebook.repository;

import com.cinebook.entity.Notification;
import com.cinebook.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    /**
     * Lấy danh sách thông báo của người dùng sắp xếp theo thời gian tạo mới nhất.
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Đếm số lượng thông báo chưa đọc của người dùng.
     */
    long countByUserIdAndIsReadFalse(String userId);

    /**
     * Tìm thông báo theo ID và đảm bảo thuộc quyền sở hữu của người dùng.
     */
    Optional<Notification> findByIdAndUserId(String id, String userId);

    /**
     * Đánh dấu tất cả thông báo chưa đọc của người dùng là đã đọc.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") String userId, @Param("now") LocalDateTime now);

    /**
     * Kiểm tra sự tồn tại của thông báo cho một đơn hàng và loại sự kiện (Kiểm tra Idempotent mức ứng dụng).
     */
    boolean existsByBookingIdAndType(String bookingId, NotificationType type);
}

