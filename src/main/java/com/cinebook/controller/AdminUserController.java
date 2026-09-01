package com.cinebook.controller;

import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.UserProfileResponse;
import com.cinebook.enums.UserStatus;
import com.cinebook.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin User", description = "Administrator user management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "List and search users for administration")
    @GetMapping
    public ResponseEntity<PageResponse<UserProfileResponse>> getAdminUsers(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) UserStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<UserProfileResponse> response = userService.getAdminUsers(q, status, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update user status (ACTIVE, INACTIVE, LOCKED)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserProfileResponse> updateUserStatus(
            @PathVariable String id,
            @RequestParam(name = "status") UserStatus status
    ) {
        UserProfileResponse response = userService.updateUserStatus(id, status);
        return ResponseEntity.ok(response);
    }
}