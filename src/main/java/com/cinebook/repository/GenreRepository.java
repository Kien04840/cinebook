package com.cinebook.repository;

import com.cinebook.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, String> {

    Optional<Genre> findByName(String name);

    boolean existsByName(String name);

    Optional<Genre> findByTmdbId(Long tmdbId);

    boolean existsByTmdbId(Long tmdbId);
}