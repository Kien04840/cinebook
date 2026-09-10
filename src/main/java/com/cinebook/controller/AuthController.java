package com.cinebook.controller;

import com.cinebook.dto.request.LoginRequest;
import com.cinebook.dto.request.PasswordResetConfirmRequest;
import com.cinebook.dto.request.PasswordResetRequest;
import com.cinebook.dto.request.RefreshTokenRequest;
import com.cinebook.dto.request.RegisterRequest;
import com.cinebook.dto.request.ResendVerificationRequest;
import com.cinebook.dto.request.VerifyEmailRequest;
import com.cinebook.dto.response.AuthResponse;
import com.cinebook.dto.response.MessageResponse;
import com.cinebook.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận và điều phối các yêu cầu xác thực người dùng (Authentication Controller).
 * Cung cấp các endpoint công khai (Public API) cho các tiến trình:
 * - Đăng ký tài khoản khách hàng mới (/register)
 * - Đăng nhập bằng Email/Password (/login)
 * - Làm mới phiên Access Token (/refresh)
 * - Đăng xuất và hủy phiên (/logout)
 * - Quên mật khẩu và đặt lại mật khẩu (/password-reset/**)
 */
@Tag(name = "Authentication", description = "Các API xác thực, phân quyền và quản lý phiên đăng nhập")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Endpoint đăng ký tài khoản khách hàng mới.
     * Trả về mã HTTP 201 CREATED kèm thông tin người dùng và bộ token (Access + Refresh).
     */
    @Operation(summary = "Đăng ký tài khoản khách hàng mới")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint đăng nhập bằng email và mật khẩu.
     * Trả về mã HTTP 200 OK kèm token xác thực nếu thông tin đăng nhập chính xác.
     */
    @Operation(summary = "Đăng nhập tài khoản bằng email và mật khẩu")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint làm mới Access Token khi token cũ hết hạn (15 phút).
     * Áp dụng cơ chế Token Rotation: cấp mới đồng thời cả Access Token và Refresh Token.
     */
    @Operation(summary = "Làm mới Access Token bằng Refresh Token (Token Rotation)")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint đăng xuất: Thu hồi Refresh Token trong cơ sở dữ liệu để chấm dứt phiên làm việc.
     */
    @Operation(summary = "Đăng xuất và thu hồi Refresh Token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        String token = request != null ? request.getRefreshToken() : null;
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint yêu cầu gửi email đặt lại mật khẩu khi người dùng quên mật khẩu.
     */
    @Operation(summary = "Yêu cầu gửi email đặt lại mật khẩu")
    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(new MessageResponse("If the account exists, a password reset email has been sent"));
    }

    /**
     * Endpoint xác nhận mã token và thiết lập mật khẩu mới.
     */
    @Operation(summary = "Xác nhận mã token và đặt lại mật khẩu mới")
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<MessageResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.ok(new MessageResponse("Password has been reset successfully"));
    }

    /**
     * Endpoint xác thực email người dùng thông qua mã token nhận từ email.
     */
    @Operation(summary = "Xác thực email người dùng qua token")
    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        authService.verifyEmail(request.getToken());
        return ResponseEntity.ok(new MessageResponse("Email has been verified successfully"));
    }

    /**
     * Endpoint gửi lại email xác thực cho người dùng.
     */
    @Operation(summary = "Gửi lại email xác thực")
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request
    ) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("If your email is registered and eligible, a verification link has been sent"));
    }
}

