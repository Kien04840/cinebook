package com.cinebook.service;

import com.cinebook.dto.request.LoginRequest;
import com.cinebook.dto.request.PasswordResetConfirmRequest;
import com.cinebook.dto.request.PasswordResetRequest;
import com.cinebook.dto.request.RefreshTokenRequest;
import com.cinebook.dto.request.RegisterRequest;
import com.cinebook.dto.response.AuthResponse;
import com.cinebook.entity.PasswordResetToken;
import com.cinebook.entity.RefreshToken;
import com.cinebook.entity.Role;
import com.cinebook.entity.User;
import com.cinebook.enums.UserStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.UnauthorizedException;
import com.cinebook.mapper.UserMapper;
import com.cinebook.repository.PasswordResetTokenRepository;
import com.cinebook.repository.RefreshTokenRepository;
import com.cinebook.repository.RoleRepository;
import com.cinebook.repository.UserRepository;
import com.cinebook.repository.UserRoleRepository;
import com.cinebook.security.JwtTokenProvider;
import com.cinebook.service.impl.AuthServiceImpl;
import com.cinebook.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604800000L);
        ReflectionTestUtils.setField(authService, "resetTokenExpiration", 900000L);

        customerRole = new Role();
        customerRole.setId("role-customer-id");
        customerRole.setName("CUSTOMER");

        sampleUser = new User();
        sampleUser.setId("user-123");
        sampleUser.setEmail("test@example.com");
        sampleUser.setPasswordHash("encoded_password");
        sampleUser.setFullName("Test User");
        sampleUser.setPhone("0901234567");
        sampleUser.setStatus(UserStatus.ACTIVE);
        sampleUser.setEmailVerified(false);
    }

    @Test
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("password123")
                .fullName("Test User")
                .phone("0901234567")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234567")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("user-123");
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("mock-access-token");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(900L);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("test@example.com", response.getUser().getEmail());

        verify(userRepository).save(any(User.class));
        verify(userRoleRepository).save(any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void register_DuplicateEmail_ThrowsConflict() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("password123")
                .fullName("Test User")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_DuplicatePhone_ThrowsConflict() {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com")
                .password("password123")
                .fullName("Test User")
                .phone("0901234567")
                .build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234567")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(request));
    }

    @Test
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("password123", "encoded_password")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("mock-access-token");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertNotNull(sampleUser.getLastLoginAt());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void login_InvalidPassword_ThrowsBadCredentials() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("wrong_password")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_password", "encoded_password")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_BlockedUser_ThrowsUnauthorized() {
        sampleUser.setStatus(UserStatus.BLOCKED);
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }

    @Test
    void refreshToken_Success() {
        String rawToken = "sample-raw-refresh-token";
        String tokenHash = HashUtils.sha256(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId("token-id");
        refreshToken.setUser(sampleUser);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("new-access-token");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(900L);

        AuthResponse response = authService.refreshToken(new RefreshTokenRequest(rawToken));

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertNotNull(refreshToken.getRevokedAt()); // rotated
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refreshToken_Expired_ThrowsUnauthorized() {
        String rawToken = "expired-token";
        String tokenHash = HashUtils.sha256(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(sampleUser);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // expired

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(new RefreshTokenRequest(rawToken)));
    }

    @Test
    void requestPasswordReset_UserExists_SendsEmail() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));

        authService.requestPasswordReset(new PasswordResetRequest("test@example.com"));

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), anyString());
    }

    @Test
    void confirmPasswordReset_Success() {
        String rawToken = "valid-reset-token";
        String tokenHash = HashUtils.sha256(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(sampleUser);
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(15));

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new_encoded_pwd");

        authService.confirmPasswordReset(new PasswordResetConfirmRequest(rawToken, "newPassword123"));

        assertEquals("new_encoded_pwd", sampleUser.getPasswordHash());
        assertNotNull(resetToken.getUsedAt());
        verify(userRepository).save(sampleUser);
        verify(passwordResetTokenRepository).save(resetToken);
        verify(refreshTokenRepository).deleteByUserId(sampleUser.getId());
    }

    @Test
    void confirmPasswordReset_AlreadyUsed_ThrowsBadRequest() {
        String rawToken = "used-token";
        String tokenHash = HashUtils.sha256(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(sampleUser);
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        resetToken.setUsedAt(LocalDateTime.now().minusMinutes(5));

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThrows(BadRequestException.class, () ->
                authService.confirmPasswordReset(new PasswordResetConfirmRequest(rawToken, "newPassword123"))
        );
    }
}

