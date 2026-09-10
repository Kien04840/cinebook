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
    name = "email_verification_tokens",
    indexes = {
        @Index(
            name = "idx_email_verification_tokens_user",
            columnList = "user_id"
        ),
        @Index(
            name = "idx_email_verification_tokens_expires",
            columnList = "expires_at"
        )
    }
)
public class EmailVerificationToken {

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
            name = "fk_email_verification_tokens_user"
        )
    )
    private User user;

    @Column(
        length = 100,
        nullable = false,
        unique = true,
        updatable = false
    )
    private String token;

    @Column(
        name = "expires_at",
        nullable = false
    )
    private LocalDateTime expiresAt;

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

