package com.cinebook.service;

import com.cinebook.dto.request.ChangePasswordRequest;
import com.cinebook.dto.request.UpdateProfileRequest;
import com.cinebook.dto.response.UserProfileResponse;

public interface UserService {

    UserProfileResponse getCurrentProfile();

    UserProfileResponse updateCurrentProfile(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);
}

