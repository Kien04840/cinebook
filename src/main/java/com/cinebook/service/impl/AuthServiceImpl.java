package com.cinebook.service.impl;

import com.cinebook.dto.request.LoginRequest;
import com.cinebook.dto.request.PasswordResetConfirmRequest;
import com.cinebook.dto.request.PasswordResetRequest;
import com.cinebook.dto.request.RefreshTokenRequest;
import com.cinebook.dto.request.RegisterRequest;
import com.cinebook.dto.response.AuthResponse;
import com.cinebook.dto.response.UserResponse;
import com.cinebook.entity.PasswordResetToken;
import com.cinebook.entity.RefreshToken;
import com.cinebook.entity.Role;
import com.cinebook.entity.User;
import com.cinebook.entity.UserRole;
import com.cinebook.entity.UserRoleId;
import com.cinebook.enums.UserStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.exception.UnauthorizedException;
import com.cinebook.mapper.UserMapper;
import com.cinebook.repository.PasswordResetTokenRepository;
import com.cinebook.repository.RefreshTokenRepository;
import com.cinebook.repository.RoleRepository;
import com.cinebook.repository.UserRepository;
import com.cinebook.repository.UserRoleRepository;
import com.cinebook.security.JwtTokenProvider;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.AuthService;
import com.cinebook.service.EmailService;
import com.cinebook.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final EmailService emailService;

    @Value("${jwt.refresh-token.expiration:604800000}")
    private long refreshTokenExpiration;

    @Value("${jwt.reset-token.expiration:900000}")
    private long resetTokenExpiration;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already in use: " + email);
        }

        String phone = StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null;
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new ConflictException("Phone number is already in use: " + phone);
        }

        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName("CUSTOMER");
                    newRole.setDescription("Default customer role");
                    return roleRepository.save(newRole);
                });

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setPhone(phone);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(savedUser.getId(), customerRole.getId()));
        userRole.setUser(savedUser);
        userRole.setRole(customerRole);

        savedUser.addUserRole(userRole);

        UserDetailsImpl userDetails = UserDetailsImpl.build(savedUser);
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String rawRefreshToken = createRefreshToken(savedUser);

        UserResponse userResponse = userMapper.toUserResponse(savedUser);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .user(userResponse)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.getDeletedAt() != null || user.getStatus() == UserStatus.BLOCKED) {
            throw new UnauthorizedException("Account is blocked or deactivated");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String rawRefreshToken = createRefreshToken(user);

        UserResponse userResponse = userMapper.toUserResponse(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .user(userResponse)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        String tokenHash = HashUtils.sha256(rawToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (storedToken.getRevokedAt() != null) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        User user = storedToken.getUser();
        if (user.getDeletedAt() != null || user.getStatus() == UserStatus.BLOCKED) {
            throw new UnauthorizedException("Account is blocked or deactivated");
        }

        // Rotate refresh token
        storedToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(storedToken);

        String newRawRefreshToken = createRefreshToken(user);
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);

        UserResponse userResponse = userMapper.toUserResponse(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .user(userResponse)
                .build();
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (StringUtils.hasText(rawRefreshToken)) {
            String tokenHash = HashUtils.sha256(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                if (token.getRevokedAt() == null) {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                }
            });
        }
    }

    @Override
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getDeletedAt() == null && user.getStatus() == UserStatus.ACTIVE) {
                String rawResetToken = UUID.randomUUID().toString();
                String tokenHash = HashUtils.sha256(rawResetToken);

                PasswordResetToken resetToken = new PasswordResetToken();
                resetToken.setUser(user);
                resetToken.setTokenHash(tokenHash);
                resetToken.setExpiresAt(LocalDateTime.now().plus(resetTokenExpiration, ChronoUnit.MILLIS));
                passwordResetTokenRepository.save(resetToken);

                emailService.sendPasswordResetEmail(user.getEmail(), rawResetToken);
            }
        }
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        String tokenHash = HashUtils.sha256(request.getToken());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));

        if (resetToken.getUsedAt() != null) {
            throw new BadRequestException("Password reset token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        // Revoke existing refresh tokens for security
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    private String createRefreshToken(User user) {
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = HashUtils.sha256(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plus(refreshTokenExpiration, ChronoUnit.MILLIS));
        refreshTokenRepository.save(refreshToken);

        return rawRefreshToken;
    }
}

