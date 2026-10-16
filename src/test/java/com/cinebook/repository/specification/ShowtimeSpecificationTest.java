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
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowtimeSpecificationTest {

    @Mock
    private Root<Showtime> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Join<Showtime, Movie> movieJoin;

    @Mock
    private Join<Showtime, Auditorium> auditoriumJoin;

    @Mock
    private Join<Auditorium, Cinema> cinemaJoin;

    @Mock
    private Path<Object> statusPath;

    @Mock
    private Path<LocalDateTime> startTimePath;

    @Mock
    private Predicate andPredicate;

    @BeforeEach
    void setUp() {
        when(root.<Showtime, Movie>join("movie")).thenReturn(movieJoin);
        when(root.<Showtime, Auditorium>join("auditorium")).thenReturn(auditoriumJoin);
        when(auditoriumJoin.<Auditorium, Cinema>join("cinema")).thenReturn(cinemaJoin);
    }

    @Test
    void isPubliclyVisible_BuildsPredicateWithFutureStartTimeAndScheduledStatus() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 12, 0);
        when(root.get("status")).thenReturn(statusPath);
        when(root.<LocalDateTime>get("startTime")).thenReturn(startTimePath);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);

        Specification<Showtime> spec = ShowtimeSpecification.isPubliclyVisible(now);
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        verify(cb).equal(statusPath, ShowtimeStatus.SCHEDULED);
        verify(cb).greaterThan(startTimePath, now);
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    void isPubliclyVisible_DefaultNow_BuildsPredicate() {
        when(root.get("status")).thenReturn(statusPath);
        when(root.<LocalDateTime>get("startTime")).thenReturn(startTimePath);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);

        Specification<Showtime> spec = ShowtimeSpecification.isPubliclyVisible();
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        verify(cb).equal(statusPath, ShowtimeStatus.SCHEDULED);
        verify(cb).greaterThan(eq(startTimePath), any(LocalDateTime.class));
    }
}
