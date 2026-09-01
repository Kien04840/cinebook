package com.cinebook.controller;

import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.UserProfileResponse;
import com.cinebook.enums.UserStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminUserController adminUserController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAdminUsers_Returns200() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .id("u-1")
                .email("user@cinebook.com")
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .roles(List.of("CUSTOMER"))
                .createdAt(LocalDateTime.now())
                .build();

        PageResponse<UserProfileResponse> page = PageResponse.of(
                new PageImpl<>(List.of(profile), PageRequest.of(0, 20), 1),
                p -> p
        );

        when(userService.getAdminUsers(any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", "user@cinebook.com")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("user@cinebook.com"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void updateUserStatus_Returns200() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .id("u-1")
                .email("user@cinebook.com")
                .fullName("Test User")
                .status(UserStatus.BLOCKED)
                .roles(List.of("CUSTOMER"))
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.updateUserStatus(eq("u-1"), eq(UserStatus.BLOCKED))).thenReturn(profile);

        mockMvc.perform(patch("/api/v1/admin/users/u-1/status")
                        .param("status", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u-1"))
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }
}