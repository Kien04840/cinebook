package com.cinebook.service;

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
import com.cinebook.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Spy
    private NotificationMapper notificationMapper = new NotificationMapper();

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User sampleUser;
    private Booking sampleBooking;
    private Notification sampleNotification;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId("user-123");
        sampleUser.setEmail("customer@cinebook.com");
        sampleUser.setFullName("Nguyen Van A");

        sampleBooking = new Booking();
        sampleBooking.setId("booking-456");
        sampleBooking.setBookingCode("CB-2026-001");
        sampleBooking.setUser(sampleUser);

        sampleNotification = new Notification();
        sampleNotification.setId("notif-789");
        sampleNotification.setUser(sampleUser);
        sampleNotification.setBooking(sampleBooking);
        sampleNotification.setType(NotificationType.PAYMENT_SUCCESS);
        sampleNotification.setTitle("Thanh toán thành công");
        sampleNotification.setMessage("Đơn đặt vé #CB-2026-001 đã được thanh toán thành công.");
        sampleNotification.setRead(false);
        sampleNotification.setCreatedAt(LocalDateTime.now().minusMinutes(5));
    }

    @Test
    @DisplayName("getUserNotifications should return paged responses for current user")
    void getUserNotifications_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(List.of(sampleNotification), pageable, 1);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("user-123", pageable))
                .thenReturn(page);

        PageResponse<NotificationResponse> result = notificationService.getUserNotifications("user-123", pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("notif-789", result.getContent().get(0).getId());
        assertEquals("Thanh toán thành công", result.getContent().get(0).getTitle());
        assertEquals(NotificationType.PAYMENT_SUCCESS, result.getContent().get(0).getType());
        assertEquals("booking-456", result.getContent().get(0).getBookingId());
        assertEquals("CB-2026-001", result.getContent().get(0).getBookingCode());
        assertFalse(result.getContent().get(0).isRead());
    }

    @Test
    @DisplayName("getUnreadCount should return correct unread count")
    void getUnreadCount_Success() {
        when(notificationRepository.countByUserIdAndIsReadFalse("user-123")).thenReturn(3L);

        UnreadCountResponse result = notificationService.getUnreadCount("user-123");

        assertNotNull(result);
        assertEquals(3L, result.getUnreadCount());
        verify(notificationRepository).countByUserIdAndIsReadFalse("user-123");
    }

    @Test
    @DisplayName("markAsRead should update isRead and readAt for owned notification")
    void markAsRead_Success() {
        when(notificationRepository.findByIdAndUserId("notif-789", "user-123"))
                .thenReturn(Optional.of(sampleNotification));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead("notif-789", "user-123");

        assertNotNull(response);
        assertTrue(response.isRead());
        assertNotNull(response.getReadAt());
        verify(notificationRepository).save(sampleNotification);
    }

    @Test
    @DisplayName("markAsRead should throw ResourceNotFoundException when notification belongs to another user")
    void markAsRead_OtherUser_ThrowsNotFound() {
        when(notificationRepository.findByIdAndUserId("notif-789", "user-other"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.markAsRead("notif-789", "user-other"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAllAsRead should invoke repository markAllAsRead")
    void markAllAsRead_Success() {
        when(notificationRepository.markAllAsRead(eq("user-123"), any(LocalDateTime.class))).thenReturn(2);

        notificationService.markAllAsRead("user-123");

        verify(notificationRepository).markAllAsRead(eq("user-123"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("createNotification should save new notification when not existing")
    void createNotification_Success() {
        when(notificationRepository.existsByBookingIdAndType("booking-456", NotificationType.PAYMENT_SUCCESS))
                .thenReturn(false);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.createNotification(
                sampleUser,
                sampleBooking,
                NotificationType.PAYMENT_SUCCESS,
                "Thanh toán thành công",
                "Đơn vé đã được thanh toán"
        );

        verify(notificationRepository).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("createNotification should skip saving when notification already exists (App-level Idempotency)")
    void createNotification_Duplicate_Skipped() {
        when(notificationRepository.existsByBookingIdAndType("booking-456", NotificationType.PAYMENT_SUCCESS))
                .thenReturn(true);

        notificationService.createNotification(
                sampleUser,
                sampleBooking,
                NotificationType.PAYMENT_SUCCESS,
                "Thanh toán thành công",
                "Đơn vé đã được thanh toán"
        );

        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("createNotification should safely catch DataIntegrityViolationException from DB unique constraint (DB-level Idempotency)")
    void createNotification_DataIntegrityViolationException_Handled() {
        when(notificationRepository.existsByBookingIdAndType("booking-456", NotificationType.BOOKING_CANCELLED))
                .thenReturn(false);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry uk_notifications_booking_type"));

        assertDoesNotThrow(() -> notificationService.createNotification(
                sampleUser,
                sampleBooking,
                NotificationType.BOOKING_CANCELLED,
                "Hủy đơn",
                "Đơn vé đã hủy"
        ));

        verify(notificationRepository).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("createNotification should handle REFUND_COMPLETED type correctly")
    void createNotification_RefundCompleted() {
        when(notificationRepository.existsByBookingIdAndType("booking-456", NotificationType.REFUND_COMPLETED))
                .thenReturn(false);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.createNotification(
                sampleUser,
                sampleBooking,
                NotificationType.REFUND_COMPLETED,
                "Hoàn tiền thành công",
                "Đơn vé đã hoàn tiền"
        );

        verify(notificationRepository).saveAndFlush(argThat(n ->
                n.getType() == NotificationType.REFUND_COMPLETED &&
                n.getUser().equals(sampleUser) &&
                n.getBooking().equals(sampleBooking)
        ));
    }

    @Test
    @DisplayName("createNotification with IDs should lookup entities and delegate")
    void createNotification_WithIds_Success() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));
        when(bookingRepository.findById("booking-456")).thenReturn(Optional.of(sampleBooking));
        when(notificationRepository.existsByBookingIdAndType("booking-456", NotificationType.PAYMENT_SUCCESS))
                .thenReturn(false);

        notificationService.createNotification(
                "user-123",
                "booking-456",
                NotificationType.PAYMENT_SUCCESS,
                "Tiêu đề",
                "Nội dung"
        );

        verify(notificationRepository).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("createNotification should safely ignore null parameters")
    void createNotification_NullParams_Ignored() {
        assertDoesNotThrow(() -> notificationService.createNotification(
                (User) null,
                null,
                null,
                "Tiêu đề",
                "Nội dung"
        ));

        verify(notificationRepository, never()).saveAndFlush(any());
    }
}

