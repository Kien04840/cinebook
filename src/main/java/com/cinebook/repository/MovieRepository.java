package com.cinebook.repository;

import com.cinebook.entity.Movie;
import com.cinebook.enums.MovieStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, String> {

    Optional<Movie> findByTmdbId(Long tmdbId);

    boolean existsByTmdbId(Long tmdbId);

    Page<Movie> findByStatus(
            MovieStatus status,
            Pageable pageable
    );

    Page<Movie> findByTitleContainingIgnoreCase(
            String title,
            Pageable pageable
    );

    @Query("""
        SELECT m
        FROM Movie m
        WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(m.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    Page<Movie> searchByTitle(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}