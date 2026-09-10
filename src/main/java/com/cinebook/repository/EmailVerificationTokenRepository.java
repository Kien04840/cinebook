package com.cinebook.repository;

import com.cinebook.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, String> {

    Optional<EmailVerificationToken> findByToken(String token);

    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(String userId);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    void deleteByUserId(String userId);
}

