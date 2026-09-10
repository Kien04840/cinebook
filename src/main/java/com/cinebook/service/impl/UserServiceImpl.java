package com.cinebook.service.impl;

import com.cinebook.dto.request.AdminUpdateUserRequest;
import com.cinebook.dto.request.ChangePasswordRequest;
import com.cinebook.dto.request.UpdateProfileRequest;
import com.cinebook.dto.response.UserProfileResponse;
import com.cinebook.entity.Role;
import com.cinebook.entity.User;
import com.cinebook.entity.UserRole;
import com.cinebook.entity.UserRoleId;
import com.cinebook.enums.UserStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.UserMapper;
import com.cinebook.repository.RoleRepository;
import com.cinebook.repository.UserRepository;
import com.cinebook.service.UserService;
import com.cinebook.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentProfile() {
        String currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        return userMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateCurrentProfile(UpdateProfileRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        String newPhone = StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null;
        if (newPhone != null && !newPhone.equals(user.getPhone())) {
            if (userRepository.existsByPhone(newPhone)) {
                throw new ConflictException("Phone number is already in use: " + newPhone);
            }
            user.setPhone(newPhone);
        } else if (newPhone == null) {
            user.setPhone(null);
        }

        user.setFullName(request.getFullName().trim());
        user.setAvatarUrl(request.getAvatarUrl());

        User updatedUser = userRepository.save(user);
        return userMapper.toUserProfileResponse(updatedUser);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password does not match");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password cannot be the same as current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public com.cinebook.dto.response.PageResponse<UserProfileResponse> getAdminUsers(
            String q,
            com.cinebook.enums.UserStatus status,
            org.springframework.data.domain.Pageable pageable
    ) {
        String keyword = StringUtils.hasText(q) ? q.trim() : null;
        org.springframework.data.domain.Page<User> page = userRepository.findAdminUsers(keyword, status, pageable);
        return com.cinebook.dto.response.PageResponse.of(page, userMapper::toUserProfileResponse);
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserStatus(String userId, com.cinebook.enums.UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String currentAdminId = SecurityUtils.getCurrentUserId();

        // 1. Chặn Admin tự khóa tài khoản của chính mình (Self-disable protection)
        if (userId.equals(currentAdminId) && status == UserStatus.BLOCKED) {
            throw new BadRequestException("Administrators cannot disable their own account");
        }

        // 2. Bảo vệ không để mất Admin hoạt động cuối cùng (Last Active Admin Protection)
        boolean wasActiveAdmin = user.getStatus() == UserStatus.ACTIVE
                && user.getDeletedAt() == null
                && user.getUserRoles() != null
                && user.getUserRoles().stream()
                    .anyMatch(ur -> ur.getRole() != null &&
                            ("ADMIN".equalsIgnoreCase(ur.getRole().getName()) || "ROLE_ADMIN".equalsIgnoreCase(ur.getRole().getName())));

        if (wasActiveAdmin && status == UserStatus.BLOCKED) {
            // Khóa bi quan bản ghi Role 'ADMIN' trên MySQL để tuần tự hóa an toàn các thao tác giảm số lượng Admin đồng thời
            if (roleRepository != null) {
                roleRepository.findByNameWithLock("ADMIN");
            }
            long activeAdminCount = userRepository.countActiveAdmins();
            if (activeAdminCount <= 1) {
                throw new BadRequestException("Cannot disable the last active administrator in the system");
            }
        }

        user.setStatus(status);
        User saved = userRepository.save(user);
        return userMapper.toUserProfileResponse(saved);
    }

    @Override
    @Transactional
    public UserProfileResponse adminUpdateUser(String userId, AdminUpdateUserRequest request) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String currentAdminId = SecurityUtils.getCurrentUserId();

        // 1. Chặn Admin tự khóa tài khoản của chính mình (Self-disable protection)
        if (userId.equals(currentAdminId) && request.getStatus() == UserStatus.BLOCKED) {
            throw new BadRequestException("Administrators cannot disable their own account");
        }

        // 2. Chuẩn hóa và xác thực vai trò (Admin Role Normalization)
        Set<String> normalizedRoles = new HashSet<>();
        if (request.getRoles() != null) {
            for (String roleStr : request.getRoles()) {
                if (!StringUtils.hasText(roleStr)) continue;
                String upper = roleStr.trim().toUpperCase();
                if (upper.startsWith("ROLE_")) {
                    upper = upper.substring(5);
                }
                if (!upper.equals("ADMIN") && !upper.equals("CUSTOMER")) {
                    throw new BadRequestException("Invalid role: " + roleStr);
                }
                normalizedRoles.add(upper);
            }
        }
        if (normalizedRoles.isEmpty()) {
            throw new BadRequestException("At least one valid role is required");
        }

        // 3. Chặn Admin tự tước quyền Quản trị viên của chính mình (Self-demotion protection)
        if (userId.equals(currentAdminId) && !normalizedRoles.contains("ADMIN")) {
            throw new BadRequestException("Administrators cannot remove the ADMIN role from their own account");
        }

        // 4. Bảo vệ không để mất Admin hoạt động cuối cùng (Last Active Admin Protection)
        boolean wasActiveAdmin = targetUser.getStatus() == UserStatus.ACTIVE
                && targetUser.getDeletedAt() == null
                && targetUser.getUserRoles() != null
                && targetUser.getUserRoles().stream()
                    .anyMatch(ur -> ur.getRole() != null &&
                            ("ADMIN".equalsIgnoreCase(ur.getRole().getName()) || "ROLE_ADMIN".equalsIgnoreCase(ur.getRole().getName())));

        boolean willBeActiveAdmin = request.getStatus() == UserStatus.ACTIVE && normalizedRoles.contains("ADMIN");

        if (wasActiveAdmin && !willBeActiveAdmin) {
            // Khóa bi quan bản ghi Role 'ADMIN' trên MySQL để tuần tự hóa an toàn các thao tác giảm số lượng Admin đồng thời
            if (roleRepository != null) {
                roleRepository.findByNameWithLock("ADMIN");
            }
            long activeAdminCount = userRepository.countActiveAdmins();
            if (activeAdminCount <= 1) {
                throw new BadRequestException("Cannot disable or demote the last active administrator in the system");
            }
        }

        // 5. Cập nhật họ tên và trạng thái (Email, Phone, Password tuyệt đối không bị thay đổi bởi Admin)
        targetUser.setFullName(request.getFullName().trim());
        targetUser.setStatus(request.getStatus());

        // 6. Đồng bộ hóa vai trò người dùng (user_roles)
        if (roleRepository != null) {
            // Loại bỏ các role không còn trong danh sách cập nhật
            targetUser.getUserRoles().removeIf(ur -> {
                if (ur.getRole() == null) return true;
                String rName = ur.getRole().getName().startsWith("ROLE_")
                        ? ur.getRole().getName().substring(5)
                        : ur.getRole().getName();
                return !normalizedRoles.contains(rName.toUpperCase());
            });

            // Bổ sung các role mới được cấp
            for (String rName : normalizedRoles) {
                boolean exists = targetUser.getUserRoles().stream().anyMatch(ur -> {
                    if (ur.getRole() == null) return false;
                    String n = ur.getRole().getName().startsWith("ROLE_")
                            ? ur.getRole().getName().substring(5)
                            : ur.getRole().getName();
                    return n.equalsIgnoreCase(rName);
                });

                if (!exists) {
                    Role role = roleRepository.findByName(rName)
                            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + rName));
                    UserRole userRole = new UserRole();
                    userRole.setId(new UserRoleId(targetUser.getId(), role.getId()));
                    userRole.setUser(targetUser);
                    userRole.setRole(role);
                    targetUser.getUserRoles().add(userRole);
                }
            }
        }

        User saved = userRepository.save(targetUser);
        return userMapper.toUserProfileResponse(saved);
    }
}

