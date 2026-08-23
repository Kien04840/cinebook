package com.cinebook.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "genres")
public class Genre {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 100, nullable = false, unique = true)
    private String name;

    @Column(length = 255)
    private String description;

    @OneToMany(
        mappedBy = "genre",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private Set<MovieGenre> movieGenres = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}