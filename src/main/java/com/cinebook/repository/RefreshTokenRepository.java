package com.cinebook.repository;

import com.cinebook.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    void deleteByUserId(String userId);
}