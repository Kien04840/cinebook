package com.cinebook.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 50, nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(
        mappedBy = "role",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private Set<UserRole> userRoles = new HashSet<>();

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