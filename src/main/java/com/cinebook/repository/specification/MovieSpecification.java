package com.cinebook.repository.specification;

import com.cinebook.entity.Genre;
import com.cinebook.entity.Movie;
import com.cinebook.entity.MovieGenre;
import com.cinebook.enums.MovieStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class MovieSpecification {

    private MovieSpecification() {
    }

    public static Specification<Movie> isNotDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Movie> isPubliclyVisible() {
        return (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                cb.notEqual(root.get("status"), MovieStatus.HIDDEN)
        );
    }

    public static Specification<Movie> hasStatus(MovieStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return null;
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Movie> searchKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("originalTitle")), pattern)
            );
        };
    }

    public static Specification<Movie> hasGenre(String genre) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(genre)) {
                return null;
            }
            query.distinct(true);
            Join<Movie, MovieGenre> movieGenreJoin = root.join("movieGenres");
            Join<MovieGenre, Genre> genreJoin = movieGenreJoin.join("genre");
            return cb.or(
                    cb.equal(cb.lower(genreJoin.get("name")), genre.trim().toLowerCase()),
                    cb.equal(genreJoin.get("id"), genre.trim())
            );
        };
    }
}

