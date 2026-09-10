package com.cinebook.service.impl;

import com.cinebook.dto.request.LoginRequest;
import com.cinebook.dto.request.PasswordResetConfirmRequest;
import com.cinebook.dto.request.PasswordResetRequest;
import com.cinebook.dto.request.RefreshTokenRequest;
import com.cinebook.dto.request.RegisterRequest;
import com.cinebook.dto.response.AuthResponse;
import com.cinebook.dto.response.UserResponse;
import com.cinebook.entity.EmailVerificationToken;
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
import com.cinebook.repository.EmailVerificationTokenRepository;
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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Dịch vụ xử lý toàn bộ nghiệp vụ xác thực và phân quyền tài khoản người dùng (Authentication Service).
 * 
 * Kiến trúc & Chiến lược bảo mật:
 * 1. Mật khẩu: Được mã hóa một chiều bằng BCrypt với Salt ngẫu nhiên trước khi lưu DB.
 * 2. Cơ chế Dual-Token:
 *    - Access Token: Ngắn hạn (15 phút), stateless, dùng để truy cập API qua Bearer header.
 *    - Refresh Token: Dài hạn (7 ngày), lưu trong DB dưới dạng băm SHA-256 (HashUtils.sha256).
 * 3. Token Rotation: Khi làm mới Access Token, Refresh Token cũ sẽ bị thu hồi ngay lập tức (revokedAt)
 *    và một Refresh Token mới được cấp phát, triệt tiêu nguy cơ Replay Attack khi Refresh Token bị đánh cắp.
 * 4. Password Reset: Token đặt lại mật khẩu có hiệu lực 15 phút, gửi qua email, tự động hủy toàn bộ
 *    các phiên đăng nhập cũ (xóa Refresh Tokens) sau khi đổi mật khẩu thành công.
 */
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
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${jwt.refresh-token.expiration:604800000}")
    private long refreshTokenExpiration;

    @Value("${jwt.reset-token.expiration:900000}")
    private long resetTokenExpiration;

    /**
     * Đăng ký tài khoản khách hàng mới (Customer Registration).
     * 
     * Các bước xử lý:
     * 1. Chuẩn hóa email (chuyển chữ thường, trim khoảng trắng) và kiểm tra tính duy nhất.
     * 2. Kiểm tra số điện thoại không trùng lặp nếu có cung cấp.
     * 3. Tìm hoặc khởi tạo vai trò mặc định "CUSTOMER".
     * 4. Băm mật khẩu bằng BCrypt và lưu thông tin tài khoản ở trạng thái ACTIVE.
     * 5. Liên kết bảng phân quyền user_roles giữa User và Role.
     * 6. Cấp phát cặp Access Token và Refresh Token để người dùng tự động đăng nhập ngay.
     * 
     * @param request DTO chứa email, password, fullName, phone
     * @return AuthResponse chứa cặp tokens và thông tin tài khoản
     */
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // 1. Kiểm tra ràng buộc duy nhất của email
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already in use: " + email);
        }

        // 2. Kiểm tra ràng buộc duy nhất của số điện thoại
        String phone = StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null;
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new ConflictException("Phone number is already in use: " + phone);
        }

        // 3. Lấy hoặc tạo vai trò CUSTOMER mặc định cho khách hàng
        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName("CUSTOMER");
                    newRole.setDescription("Default customer role");
                    return roleRepository.save(newRole);
                });

        // 4. Khởi tạo thực thể User với mật khẩu đã băm bằng BCrypt
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setPhone(phone);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);

        // 5. Gán vai trò cho người dùng
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(savedUser.getId(), customerRole.getId()));
        userRole.setUser(savedUser);
        userRole.setRole(customerRole);

        savedUser.addUserRole(userRole);

        // 5.1 Tạo email verification token và gửi email xác thực (Non-blocking V1)
        try {
            String verificationToken = generateVerificationToken();
            EmailVerificationToken emailToken = new EmailVerificationToken();
            emailToken.setUser(savedUser);
            emailToken.setToken(verificationToken);
            emailToken.setExpiresAt(LocalDateTime.now().plusHours(24));
            emailVerificationTokenRepository.save(emailToken);

            emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getFullName(), verificationToken);
        } catch (Exception e) {
            log.error("Failed to generate/send email verification token during registration for {}: {}", savedUser.getEmail(), e.getMessage());
        }

        // 6. Tạo phiên đăng nhập đầu tiên
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

    /**
     * Xác thực đăng nhập người dùng bằng email và mật khẩu (Authentication Login).
     * 
     * Các bước xử lý:
     * 1. Tìm tài khoản người dùng theo email.
     * 2. Kiểm tra trạng thái tài khoản: không bị xóa mềm (deletedAt == null) và không bị khóa (BLOCKED).
     * 3. Đối chiếu mật khẩu bằng BCryptPasswordEncoder.matches.
     * 4. Ghi nhận thời điểm đăng nhập gần nhất (lastLoginAt).
     * 5. Cấp phát Access Token (15 phút) và Refresh Token mới (7 ngày).
     */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // 1. Kiểm tra sự tồn tại của tài khoản
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // 2. Kiểm tra trạng thái tài khoản
        if (user.getDeletedAt() != null || user.getStatus() == UserStatus.BLOCKED) {
            throw new UnauthorizedException("Account is blocked or deactivated");
        }

        // 3. So khớp mật khẩu đã băm
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // 4. Cập nhật mốc thời gian đăng nhập
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 5. Sinh tokens và trả về kết quả
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

    /**
     * Làm mới Access Token thông qua Refresh Token (Token Rotation).
     * 
     * Kỹ thuật bảo mật:
     * 1. Băm chuỗi raw token do client gửi lên bằng SHA-256 để truy vấn DB (không lưu plain text).
     * 2. Kiểm tra token có bị thu hồi (revokedAt != null) hoặc hết hạn hay không.
     * 3. Kiểm tra tài khoản người dùng có đang hoạt động hay không.
     * 4. Thu hồi Refresh Token hiện tại (Revoke) và sinh một Refresh Token hoàn toàn mới cùng Access Token mới.
     */
    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        String tokenHash = HashUtils.sha256(rawToken);

        // 1. Tìm refresh token theo mã băm SHA-256
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // 2. Kiểm tra xem token đã từng bị thu hồi hay chưa
        if (storedToken.getRevokedAt() != null) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        // 3. Kiểm tra hạn sử dụng của refresh token
        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        // 4. Kiểm tra tài khoản có bị khóa/xóa không
        User user = storedToken.getUser();
        if (user.getDeletedAt() != null || user.getStatus() == UserStatus.BLOCKED) {
            throw new UnauthorizedException("Account is blocked or deactivated");
        }

        // 5. Thực hiện Token Rotation: Đánh dấu đã thu hồi token cũ
        storedToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(storedToken);

        // 6. Cấp phát Refresh Token mới và Access Token mới
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

    /**
     * Đăng xuất người dùng: Thu hồi (Revoke) Refresh Token để không thể làm mới phiên được nữa.
     */
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

    /**
     * Yêu cầu đặt lại mật khẩu: Sinh mã xác thực ngẫu nhiên gửi tới email của người dùng.
     * Mã có hiệu lực trong 15 phút.
     */
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

    /**
     * Xác nhận đổi mật khẩu mới bằng mã reset token nhận được từ email.
     * Sau khi đổi mật khẩu thành công, toàn bộ refresh tokens cũ bị xóa để buộc đăng nhập lại.
     */
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

        // Hủy toàn bộ refresh token cũ để bảo vệ tài khoản sau khi đổi mật khẩu
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

    /**
     * Xác thực email tài khoản bằng token nhận từ liên kết email.
     * Token là single-use và có hiệu lực 24 giờ. Idempotent nếu tài khoản đã xác thực.
     */
    @Override
    @Transactional
    public void verifyEmail(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BadRequestException("Mã xác thực không hợp lệ hoặc không tồn tại");
        }

        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token.trim())
                .orElseThrow(() -> new BadRequestException("Mã xác thực không hợp lệ hoặc không tồn tại"));

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            emailVerificationTokenRepository.delete(verificationToken);
            throw new BadRequestException("Mã xác thực đã hết hạn. Vui lòng yêu cầu gửi lại email xác thực.");
        }

        User user = verificationToken.getUser();
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("Email verified successfully for user: {}", user.getEmail());
        }

        emailVerificationTokenRepository.delete(verificationToken);
    }

    /**
     * Gửi lại email xác thực tài khoản.
     * Áp dụng chống rò rỉ tài khoản (anti-enumeration) và giới hạn tần suất (rate limit 60s).
     */
    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BadRequestException("Email is required");
        }

        String normalizedEmail = email.toLowerCase().trim();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        // Chống lộ thông tin (anti-enumeration): nếu không tìm thấy user hoặc user không active, return silently
        if (userOpt.isEmpty()) {
            log.info("Resend verification requested for non-existent email: {}", normalizedEmail);
            return;
        }

        User user = userOpt.get();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            log.info("Resend verification requested for inactive/deleted user: {}", normalizedEmail);
            return;
        }

        if (user.isEmailVerified()) {
            log.info("Resend verification requested for already verified user: {}", normalizedEmail);
            return;
        }

        // Giới hạn tần suất 60 giây giữa các lần gửi lại
        Optional<EmailVerificationToken> latestToken = emailVerificationTokenRepository
                .findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latestToken.isPresent()) {
            LocalDateTime createdAt = latestToken.get().getCreatedAt();
            if (createdAt != null && createdAt.isAfter(LocalDateTime.now().minusSeconds(60))) {
                throw new BadRequestException("Vui lòng đợi 60 giây trước khi yêu cầu gửi lại email xác thực");
            }
        }

        emailVerificationTokenRepository.deleteByUserId(user.getId());

        String verificationToken = generateVerificationToken();
        EmailVerificationToken newToken = new EmailVerificationToken();
        newToken.setUser(user);
        newToken.setToken(verificationToken);
        newToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        emailVerificationTokenRepository.save(newToken);

        try {
            emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationToken);
        } catch (Exception e) {
            log.error("Failed to send resend-verification email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private String generateVerificationToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}

