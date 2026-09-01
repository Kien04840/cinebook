package com.cinebook.mapper;

import com.cinebook.dto.response.UserProfileResponse;
import com.cinebook.dto.response.UserResponse;
import com.cinebook.dto.response.UserSummaryResponse;
import com.cinebook.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }

        List<String> roles = extractRoleNames(user);

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .build();
    }

    public UserProfileResponse toUserProfileResponse(User user) {
        if (user == null) {
            return null;
        }

        List<String> roles = extractRoleNames(user);

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public UserSummaryResponse toUserSummaryResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserSummaryResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .build();

    }

    private List<String> extractRoleNames(User user) {
        if (user.getUserRoles() == null) {
            return Collections.emptyList();
        }
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> {
                    String name = ur.getRole().getName();
                    return name.startsWith("ROLE_") ? name.substring(5) : name;
                })
                .toList();
    }
}

