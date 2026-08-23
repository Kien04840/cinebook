package com.cinebook.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = {
        @Index(
            name = "idx_password_reset_tokens_user",
            columnList = "user_id"
        ),
        @Index(
            name = "idx_password_reset_tokens_expires_at",
            columnList = "expires_at"
        )
    }
)
public class PasswordResetToken {

    @Id
    @Column(
        length = 36,
        nullable = false,
        updatable = false
    )
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "FKpassword_r298553"
        )
    )
    private User user;

    @Column(
        name = "token_hash",
        length = 255,
        nullable = false,
        unique = true,
        updatable = false
    )
    private String tokenHash;

    @Column(
        name = "expires_at",
        nullable = false
    )
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}