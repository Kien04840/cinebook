package com.cinebook.service;

import com.cinebook.dto.request.ChangePasswordRequest;
import com.cinebook.dto.request.UpdateProfileRequest;
import com.cinebook.dto.response.UserProfileResponse;

public interface UserService {

    UserProfileResponse getCurrentProfile();

    UserProfileResponse updateCurrentProfile(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);

    com.cinebook.dto.response.PageResponse<UserProfileResponse> getAdminUsers(String q, com.cinebook.enums.UserStatus status, org.springframework.data.domain.Pageable pageable);

    UserProfileResponse updateUserStatus(String userId, com.cinebook.enums.UserStatus status);
}

