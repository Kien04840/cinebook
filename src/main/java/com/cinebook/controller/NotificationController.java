package com.cinebook.controller;

import com.cinebook.dto.response.NotificationResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.UnreadCountResponse;
import com.cinebook.service.NotificationService;
import com.cinebook.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "Các API thông báo trong ứng dụng (In-App Notifications)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Lấy danh sách thông báo phân trang của người dùng hiện tại đang đăng nhập.
     */
    @Operation(summary = "Lấy danh sách thông báo phân trang của người dùng hiện tại")
    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> getMyNotifications(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        PageResponse<NotificationResponse> response = notificationService.getUserNotifications(currentUserId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy số lượng thông báo chưa đọc của người dùng hiện tại.
     */
    @Operation(summary = "Lấy số lượng thông báo chưa đọc")
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        String currentUserId = SecurityUtils.getCurrentUserId();
        UnreadCountResponse response = notificationService.getUnreadCount(currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Đánh dấu một thông báo cụ thể là đã đọc.
     */
    @Operation(summary = "Đánh dấu một thông báo là đã đọc")
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable String id) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        NotificationResponse response = notificationService.markAsRead(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Đánh dấu toàn bộ thông báo chưa đọc của người dùng là đã đọc.
     */
    @Operation(summary = "Đánh dấu tất cả thông báo là đã đọc")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        String currentUserId = SecurityUtils.getCurrentUserId();
        notificationService.markAllAsRead(currentUserId);
        return ResponseEntity.noContent().build();
    }
}

