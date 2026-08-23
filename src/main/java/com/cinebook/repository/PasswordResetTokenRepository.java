package com.cinebook.repository;

import com.cinebook.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    void deleteByUserId(String userId);
}