package com.cinebook.controller;

import com.cinebook.dto.request.ChangePasswordRequest;
import com.cinebook.dto.request.UpdateProfileRequest;
import com.cinebook.dto.response.MessageResponse;
import com.cinebook.dto.response.UserProfileResponse;
import com.cinebook.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Profile", description = "User profile and account settings endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current authenticated user profile")
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentProfile() {
        UserProfileResponse response = userService.getCurrentProfile();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update current authenticated user profile")
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UserProfileResponse response = userService.updateCurrentProfile(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Change current authenticated user password")
    @PatchMapping("/me/password")
    public ResponseEntity<MessageResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }
}

