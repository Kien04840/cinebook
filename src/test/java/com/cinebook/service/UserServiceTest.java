package com.cinebook.service;

import com.cinebook.dto.request.ChangePasswordRequest;
import com.cinebook.dto.request.UpdateProfileRequest;
import com.cinebook.dto.response.UserProfileResponse;
import com.cinebook.entity.User;
import com.cinebook.enums.UserStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.mapper.UserMapper;
import com.cinebook.repository.UserRepository;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId("user-123");
        sampleUser.setEmail("user@example.com");
        sampleUser.setFullName("Original Name");
        sampleUser.setPasswordHash("encoded_current_pwd");
        sampleUser.setPhone("0901234567");
        sampleUser.setStatus(UserStatus.ACTIVE);

        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id("user-123")
                .email("user@example.com")
                .fullName("Original Name")
                .password("encoded_current_pwd")
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .authorities(List.of())
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentProfile_Success() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));

        UserProfileResponse response = userService.getCurrentProfile();

        assertNotNull(response);
        assertEquals("user-123", response.getId());
        assertEquals("user@example.com", response.getEmail());
    }

    @Test
    void updateCurrentProfile_Success() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Updated Name")
                .phone("0909999999")
                .avatarUrl("http://example.com/avatar.jpg")
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByPhone("0909999999")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateCurrentProfile(request);

        assertNotNull(response);
        assertEquals("Updated Name", response.getFullName());
        assertEquals("0909999999", response.getPhone());
        assertEquals("http://example.com/avatar.jpg", response.getAvatarUrl());
    }

    @Test
    void updateCurrentProfile_DuplicatePhone_ThrowsConflict() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Updated Name")
                .phone("0909999999")
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByPhone("0909999999")).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.updateCurrentProfile(request));
    }

    @Test
    void changePassword_Success() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("old_password")
                .newPassword("new_password")
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("old_password", "encoded_current_pwd")).thenReturn(true);
        when(passwordEncoder.matches("new_password", "encoded_current_pwd")).thenReturn(false);
        when(passwordEncoder.encode("new_password")).thenReturn("encoded_new_pwd");

        userService.changePassword(request);

        assertEquals("encoded_new_pwd", sampleUser.getPasswordHash());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void changePassword_WrongCurrentPassword_ThrowsBadRequest() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("wrong_password")
                .newPassword("new_password")
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_password", "encoded_current_pwd")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> userService.changePassword(request));
    }
}

