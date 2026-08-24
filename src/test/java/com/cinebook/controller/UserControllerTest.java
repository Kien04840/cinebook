package com.cinebook.controller;

import com.cinebook.dto.request.ChangePasswordRequest;
import com.cinebook.dto.request.UpdateProfileRequest;
import com.cinebook.dto.response.UserProfileResponse;
import com.cinebook.enums.UserStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCurrentProfile_Returns200() throws Exception {
        UserProfileResponse response = UserProfileResponse.builder()
                .id("user-123")
                .email("user@example.com")
                .fullName("Test User")
                .phone("0901234567")
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .roles(List.of("CUSTOMER"))
                .build();

        when(userService.getCurrentProfile()).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-123"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"));
    }

    @Test
    void updateCurrentProfile_ValidRequest_Returns200() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Updated Name")
                .phone("0909999999")
                .avatarUrl("http://example.com/avatar.png")
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .id("user-123")
                .email("user@example.com")
                .fullName("Updated Name")
                .phone("0909999999")
                .avatarUrl("http://example.com/avatar.png")
                .status(UserStatus.ACTIVE)
                .roles(List.of("CUSTOMER"))
                .build();

        when(userService.updateCurrentProfile(any(UpdateProfileRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("0909999999"));
    }

    @Test
    void changePassword_ValidRequest_Returns200() throws Exception {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("old_password")
                .newPassword("new_password123")
                .build();

        doNothing().when(userService).changePassword(any(ChangePasswordRequest.class));

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }
}

