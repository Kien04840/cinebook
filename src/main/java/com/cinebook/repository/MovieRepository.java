package com.cinebook.repository;

import com.cinebook.entity.Movie;
import com.cinebook.enums.MovieStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, String>, JpaSpecificationExecutor<Movie> {

    @Override
    @EntityGraph(attributePaths = {"movieGenres.genre"})
    Optional<Movie> findById(String id);

    @EntityGraph(attributePaths = {"movieGenres.genre"})
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

    @Query("""
        SELECT m
        FROM Movie m
        WHERE m.status = :status
          AND m.releaseDate IS NOT NULL
          AND m.releaseDate <= :currentDate
          AND m.deletedAt IS NULL
    """)
    List<Movie> findComingSoonMoviesToTransition(
            @Param("status") MovieStatus status,
            @Param("currentDate") LocalDate currentDate
    );

    @Query("""
        SELECT m
        FROM Movie m
        WHERE m.deletedAt IS NULL
          AND m.status != com.cinebook.enums.MovieStatus.HIDDEN
          AND (LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY CASE WHEN m.status = com.cinebook.enums.MovieStatus.NOW_SHOWING THEN 0 ELSE 1 END,
                 m.releaseDate DESC
    """)
    List<Movie> findSuggestions(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}