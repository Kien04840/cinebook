package com.cinebook.repository.specification;

import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.enums.MovieStatus;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class ShowtimeSpecification {

    private ShowtimeSpecification() {
    }

    public static Specification<Showtime> isPubliclyVisible() {
        return (root, query, cb) -> {
            Join<Showtime, Movie> movieJoin = root.join("movie");
            Join<Showtime, Auditorium> auditoriumJoin = root.join("auditorium");
            Join<Auditorium, Cinema> cinemaJoin = auditoriumJoin.join("cinema");

            return cb.and(
                    cb.equal(root.get("status"), ShowtimeStatus.SCHEDULED),
                    cb.isNull(movieJoin.get("deletedAt")),
                    cb.notEqual(movieJoin.get("status"), MovieStatus.HIDDEN),
                    cb.isNull(auditoriumJoin.get("deletedAt")),
                    cb.equal(auditoriumJoin.get("status"), AuditoriumStatus.ACTIVE),
                    cb.isNull(cinemaJoin.get("deletedAt")),
                    cb.equal(cinemaJoin.get("status"), CinemaStatus.ACTIVE)
            );
        };
    }

    public static Specification<Showtime> hasMovieId(String movieId) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(movieId)) {
                return null;
            }
            return cb.equal(root.get("movie").get("id"), movieId.trim());
        };
    }

    public static Specification<Showtime> hasCinemaId(String cinemaId) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(cinemaId)) {
                return null;
            }
            return cb.equal(root.get("auditorium").get("cinema").get("id"), cinemaId.trim());
        };
    }

    public static Specification<Showtime> hasAuditoriumId(String auditoriumId) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(auditoriumId)) {
                return null;
            }
            return cb.equal(root.get("auditorium").get("id"), auditoriumId.trim());
        };
    }

    public static Specification<Showtime> hasStatus(ShowtimeStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return null;
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Showtime> hasFormat(ShowtimeFormat format) {
        return (root, query, cb) -> {
            if (format == null) {
                return null;
            }
            return cb.equal(root.get("format"), format);
        };
    }

    public static Specification<Showtime> hasLanguage(String language) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(language)) {
                return null;
            }
            return cb.equal(cb.lower(root.get("language")), language.trim().toLowerCase());
        };
    }

    public static Specification<Showtime> isOnDate(LocalDate date) {
        return (root, query, cb) -> {
            if (date == null) {
                return null;
            }
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
            return cb.between(root.get("startTime"), startOfDay, endOfDay);
        };
    }

    public static Specification<Showtime> isBetweenDates(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) {
                return null;
            }
            if (start != null && end != null) {
                return cb.between(root.get("startTime"), start, end);
            }
            if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("startTime"), start);
            }
            return cb.lessThanOrEqualTo(root.get("startTime"), end);
        };
    }
}