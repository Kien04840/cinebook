package com.cinebook.controller;

import com.cinebook.dto.response.NotificationResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.UnreadCountResponse;
import com.cinebook.enums.NotificationType;
import com.cinebook.enums.UserStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userDetails = UserDetailsImpl.builder()
                .id("user-123")
                .email("user@cinebook.com")
                .fullName("Nguyen Van A")
                .password("encoded_pass")
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/notifications returns 200 with PageResponse")
    void getMyNotifications_Returns200() throws Exception {
        NotificationResponse item = NotificationResponse.builder()
                .id("notif-1")
                .title("Thanh toán thành công")
                .message("Đơn đặt vé #CB-001 thành công")
                .type(NotificationType.PAYMENT_SUCCESS)
                .bookingId("booking-1")
                .bookingCode("CB-001")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        PageResponse<NotificationResponse> pageResponse = PageResponse.<NotificationResponse>builder()
                .content(List.of(item))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(notificationService.getUserNotifications(eq("user-123"), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("notif-1"))
                .andExpect(jsonPath("$.content[0].title").value("Thanh toán thành công"))
                .andExpect(jsonPath("$.content[0].type").value("PAYMENT_SUCCESS"))
                .andExpect(jsonPath("$.content[0].bookingCode").value("CB-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count returns 200 with unread count")
    void getUnreadCount_Returns200() throws Exception {
        when(notificationService.getUnreadCount("user-123"))
                .thenReturn(new UnreadCountResponse(5L));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(5));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read returns 200 with updated notification")
    void markAsRead_Returns200() throws Exception {
        NotificationResponse item = NotificationResponse.builder()
                .id("notif-1")
                .title("Thanh toán thành công")
                .type(NotificationType.PAYMENT_SUCCESS)
                .isRead(true)
                .readAt(LocalDateTime.now())
                .build();

        when(notificationService.markAsRead("notif-1", "user-123"))
                .thenReturn(item);

        mockMvc.perform(patch("/api/v1/notifications/notif-1/read")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("notif-1"))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/read-all returns 204 No Content")
    void markAllAsRead_Returns204() throws Exception {
        doNothing().when(notificationService).markAllAsRead("user-123");

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllAsRead("user-123");
    }

    @Test
    @DisplayName("GET /api/v1/notifications without authentication returns 401 Unauthorized")
    void getMyNotifications_Unauthenticated_Returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
