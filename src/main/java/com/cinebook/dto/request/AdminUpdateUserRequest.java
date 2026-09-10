package com.cinebook.dto.request;

import com.cinebook.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

/**
 * DTO yêu cầu cập nhật thông tin người dùng dành cho Quản trị viên (Admin).
 * Chỉ cho phép cập nhật họ tên, vai trò và trạng thái.
 * Tuyệt đối không chứa email, số điện thoại hoặc mật khẩu để bảo vệ quyền riêng tư người dùng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    @NotNull(message = "Status is required")
    private UserStatus status;

    @NotEmpty(message = "At least one role is required")
    private Set<String> roles;
}

