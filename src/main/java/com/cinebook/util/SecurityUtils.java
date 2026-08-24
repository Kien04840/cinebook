package com.cinebook.util;

import com.cinebook.exception.UnauthorizedException;
import com.cinebook.security.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserDetailsImpl> getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return Optional.of(userDetails);
        }

        return Optional.empty();
    }

    public static String getCurrentUserId() {
        return getCurrentUserDetails()
                .map(UserDetailsImpl::getId)
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
    }

    public static String getCurrentUserEmail() {
        return getCurrentUserDetails()
                .map(UserDetailsImpl::getEmail)
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
    }
}

