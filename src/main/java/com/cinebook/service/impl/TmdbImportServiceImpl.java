package com.cinebook.service.impl;

import com.cinebook.config.TmdbProperties;
import com.cinebook.dto.response.TmdbGenreSyncResponse;
import com.cinebook.dto.response.TmdbMovieImportResponse;
import com.cinebook.dto.tmdb.TmdbGenreDto;
import com.cinebook.dto.tmdb.TmdbGenreListResponse;
import com.cinebook.dto.tmdb.TmdbMovieDetailDto;
import com.cinebook.entity.Genre;
import com.cinebook.entity.Movie;
import com.cinebook.entity.MovieGenre;
import com.cinebook.entity.MovieGenreId;
import com.cinebook.enums.MovieStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.repository.GenreRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.service.TmdbImportService;
import com.cinebook.tmdb.TmdbClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbImportServiceImpl implements TmdbImportService {

    private static final int MAX_ACTORS = 10;
    private static final String YOUTUBE_SITE = "YouTube";
    private static final String TRAILER_TYPE = "Trailer";
    private static final String DIRECTOR_JOB = "Director";
    /** Preferred country for certification lookup (US ratings: G, PG, PG-13, R, NC-17) */
    private static final String CERTIFICATION_COUNTRY = "US";
    private static final String DEFAULT_AGE_RATING = "NR";

    private final TmdbClient tmdbClient;
    private final TmdbProperties tmdbProperties;
    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;

    // =========================================================
    //  Genre Sync
    // =========================================================

    @Override
    @Transactional
    public TmdbGenreSyncResponse syncGenres() {
        log.info("Starting TMDB genre sync");

        TmdbGenreListResponse response = tmdbClient.getMovieGenres(tmdbProperties.getLanguage());

        if (response == null || response.getGenres() == null) {
            log.warn("TMDB genre list response was null or empty");
            return TmdbGenreSyncResponse.builder()
                    .created(0).updated(0).unchanged(0).total(0)
                    .build();
        }

        int created = 0, updated = 0, unchanged = 0;

        for (TmdbGenreDto tmdbGenre : response.getGenres()) {
            if (tmdbGenre.getId() == null || !StringUtils.hasText(tmdbGenre.getName())) {
                log.warn("Skipping TMDB genre with null id or blank name: {}", tmdbGenre.getId());
                continue;
            }

            Optional<Genre> existing = genreRepository.findByTmdbId(tmdbGenre.getId().longValue());

            if (existing.isPresent()) {
                Genre genre = existing.get();
                String tmdbName = tmdbGenre.getName().trim();
                if (!tmdbName.equalsIgnoreCase(genre.getName())) {
                    log.info("Updating genre tmdbId={} name: '{}' -> '{}'", tmdbGenre.getId(), genre.getName(), tmdbName);
                    genre.setName(tmdbName);
                    genreRepository.save(genre);
                    updated++;
                } else {
                    log.debug("Genre tmdbId={} unchanged ('{}')", tmdbGenre.getId(), genre.getName());
                    unchanged++;
                }
            } else {
                Genre newGenre = new Genre();
                newGenre.setTmdbId(tmdbGenre.getId().longValue());
                newGenre.setName(tmdbGenre.getName().trim());
                genreRepository.save(newGenre);
                log.info("Created new genre tmdbId={} name='{}'", tmdbGenre.getId(), newGenre.getName());
                created++;
            }
        }

        int total = created + updated + unchanged;
        log.info("TMDB genre sync complete: created={}, updated={}, unchanged={}, total={}", created, updated, unchanged, total);

        return TmdbGenreSyncResponse.builder()
                .created(created)
                .updated(updated)
                .unchanged(unchanged)
                .total(total)
                .build();
    }

    // =========================================================
    //  Movie Import
    // =========================================================

    @Override
    @Transactional
    public TmdbMovieImportResponse importMovie(Long tmdbId) {
        log.info("Starting TMDB movie import for tmdbId={}", tmdbId);

        TmdbMovieDetailDto detail = tmdbClient.getMovieDetail(tmdbId, tmdbProperties.getLanguage());

        // Validate minimum required fields
        if (!StringUtils.hasText(detail.getTitle())) {
            throw new BadRequestException("TMDB movie (id=" + tmdbId + ") is missing required field: title");
        }
        if (!StringUtils.hasText(detail.getOverview())) {
            throw new BadRequestException("TMDB movie (id=" + tmdbId + ") is missing required field: overview");
        }
        if (!StringUtils.hasText(detail.getReleaseDate())) {
            throw new BadRequestException("TMDB movie (id=" + tmdbId + ") is missing required field: release_date");
        }

        LocalDate releaseDate = parseReleaseDate(detail.getReleaseDate(), tmdbId);

        // Resolve genres — must load from repository to avoid detached entity conflicts
        List<Genre> resolvedGenres = resolveGenres(detail.getGenres());

        // Check if movie already exists
        Optional<Movie> existingOpt = movieRepository.findByTmdbId(tmdbId);

        Movie movie;
        String action;

        if (existingOpt.isPresent()) {
            movie = existingOpt.get();
            updateMovieFromTmdb(movie, detail, releaseDate, resolvedGenres);
            action = "UPDATED";
            log.info("Updated existing movie id={} tmdbId={} title='{}'", movie.getId(), tmdbId, movie.getTitle());
        } else {
            movie = createMovieFromTmdb(detail, releaseDate, resolvedGenres);
            action = "CREATED";
            log.info("Created new movie id={} tmdbId={} title='{}'", movie.getId(), tmdbId, movie.getTitle());
        }

        Movie saved = movieRepository.save(movie);

        List<String> genreNames = saved.getMovieGenres().stream()
                .map(mg -> mg.getGenre().getName())
                .sorted()
                .toList();

        return TmdbMovieImportResponse.builder()
                .movieId(saved.getId())
                .tmdbId(saved.getTmdbId())
                .title(saved.getTitle())
                .originalTitle(saved.getOriginalTitle())
                .action(action)
                .status(saved.getStatus())
                .releaseDate(saved.getReleaseDate())
                .ageRating(saved.getAgeRating())
                .genres(genreNames)
                .posterUrl(saved.getPosterUrl())
                .trailerUrl(saved.getTrailerUrl())
                .build();
    }

    // =========================================================
    //  Private — Movie Mapping
    // =========================================================

    /**
     * Creates a brand new Movie entity from TMDB data.
     * UUID and status are determined by CineBook logic, not TMDB.
     */
    private Movie createMovieFromTmdb(TmdbMovieDetailDto detail, LocalDate releaseDate, List<Genre> resolvedGenres) {
        Movie movie = new Movie();
        movie.setTmdbId(detail.getId());

        applyTmdbFields(movie, detail, releaseDate);

        // For new movies, determine initial status from release date
        movie.setStatus(determineInitialStatus(releaseDate));

        // Set age rating
        movie.setAgeRating(extractAgeRating(detail));

        // Build genre relationships
        syncMovieGenres(movie, resolvedGenres);

        return movie;
    }

    /**
     * Updates an existing Movie with TMDB-sourced fields.
     *
     * Per business rule: DOES NOT overwrite CineBook lifecycle fields:
     * - id, tmdbId, status, deletedAt, createdAt, version
     * - JPA manages updatedAt and version automatically.
     *
     * Soft-deleted movies are updated (data sync) but remain soft-deleted.
     * Status set by admin is preserved.
     */
    private void updateMovieFromTmdb(Movie movie, TmdbMovieDetailDto detail, LocalDate releaseDate, List<Genre> resolvedGenres) {
        applyTmdbFields(movie, detail, releaseDate);
        movie.setAgeRating(extractAgeRating(detail));
        syncMovieGenres(movie, resolvedGenres);
        // status, deletedAt, createdAt, id, tmdbId, version — intentionally NOT touched
    }

    /**
     * Applies all TMDB-sourced fields to a Movie entity (shared between create and update).
     */
    private void applyTmdbFields(Movie movie, TmdbMovieDetailDto detail, LocalDate releaseDate) {
        movie.setTitle(detail.getTitle().trim());
        movie.setOriginalTitle(detail.getOriginalTitle() != null ? detail.getOriginalTitle().trim() : null);
        movie.setOverview(detail.getOverview() != null ? detail.getOverview().trim() : "");
        movie.setDurationMinutes(mapRuntime(detail.getRuntime()));
        movie.setDirector(extractDirector(detail));
        movie.setActors(extractActors(detail));
        movie.setCountry(extractCountry(detail));
        movie.setLanguage(detail.getOriginalLanguage());
        movie.setReleaseDate(releaseDate);
        movie.setPosterUrl(tmdbProperties.buildPosterUrl(detail.getPosterPath()));
        movie.setBackdropUrl(tmdbProperties.buildBackdropUrl(detail.getBackdropPath()));
        movie.setTrailerUrl(extractTrailerUrl(detail));
    }

    // =========================================================
    //  Private — MovieGenre Synchronization
    // =========================================================

    /**
     * Synchronizes the movie's genre collection in a Hibernate-safe way.
     *
     * Critical: all Genre entities MUST come from the repository (managed entities)
     * to avoid "A different object with the same identifier" errors.
     *
     * Strategy:
     * - Remove MovieGenres whose genre is no longer in the target list
     * - Add MovieGenres for new genres
     * - Keep existing MovieGenres unchanged
     *
     * orphanRemoval=true on Movie.movieGenres handles DB deletion of removed entries.
     */
    private void syncMovieGenres(Movie movie, List<Genre> targetGenres) {
        Set<String> targetGenreIds = targetGenres.stream()
                .map(Genre::getId)
                .collect(Collectors.toSet());

        // Remove genres no longer in TMDB list
        movie.getMovieGenres().removeIf(mg -> !targetGenreIds.contains(mg.getGenre().getId()));

        // Determine which genres are already linked
        Set<String> existingGenreIds = movie.getMovieGenres().stream()
                .map(mg -> mg.getGenre().getId())
                .collect(Collectors.toSet());

        // Add new genre relationships
        for (Genre genre : targetGenres) {
            if (!existingGenreIds.contains(genre.getId())) {
                MovieGenre mg = new MovieGenre();
                mg.setId(new MovieGenreId(movie.getId(), genre.getId()));
                mg.setMovie(movie);
                mg.setGenre(genre);  // managed entity from repository
                movie.getMovieGenres().add(mg);
            }
        }
    }

    // =========================================================
    //  Private — Genre Resolution
    // =========================================================

    /**
     * Resolves TMDB genres to CineBook Genre entities.
     *
     * All returned entities are loaded from the repository (managed in the current
     * persistence context) to prevent Hibernate detached entity conflicts.
     *
     * If a TMDB genre does not exist in CineBook yet, it is auto-created.
     */
    private List<Genre> resolveGenres(List<TmdbGenreDto> tmdbGenres) {
        if (tmdbGenres == null || tmdbGenres.isEmpty()) {
            return List.of();
        }

        // Build a lookup map of existing genres by tmdbId (single query)
        List<Long> tmdbGenreIds = tmdbGenres.stream()
                .filter(g -> g.getId() != null)
                .map(g -> g.getId().longValue())
                .toList();

        // Load all existing genres in one batch and index by tmdbId
        Map<Long, Genre> existingByTmdbId = genreRepository.findAll().stream()
                .filter(g -> g.getTmdbId() != null)
                .collect(Collectors.toMap(Genre::getTmdbId, g -> g));

        List<Genre> resolved = new ArrayList<>();

        for (TmdbGenreDto tmdbGenre : tmdbGenres) {
            if (tmdbGenre.getId() == null || !StringUtils.hasText(tmdbGenre.getName())) {
                continue;
            }

            long gTmdbId = tmdbGenre.getId().longValue();
            Genre genre = existingByTmdbId.get(gTmdbId);

            if (genre == null) {
                // Auto-create genre that doesn't exist yet
                Genre newGenre = new Genre();
                newGenre.setTmdbId(gTmdbId);
                newGenre.setName(tmdbGenre.getName().trim());
                genre = genreRepository.save(newGenre);
                existingByTmdbId.put(gTmdbId, genre); // prevent duplicate creation in same call
                log.info("Auto-created genre during movie import: tmdbId={} name='{}'", gTmdbId, genre.getName());
            }

            resolved.add(genre);
        }

        return resolved;
    }

    // =========================================================
    //  Private — Field Extraction Helpers
    // =========================================================

    private LocalDate parseReleaseDate(String releaseDateStr, Long tmdbId) {
        try {
            return LocalDate.parse(releaseDateStr);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(
                    "TMDB movie (id=" + tmdbId + ") has invalid release_date format: '" + releaseDateStr + "'");
        }
    }

    /**
     * Maps TMDB runtime (minutes as Integer) to Short.
     * Returns 0 if runtime is null or invalid (not a hard failure).
     */
    private Short mapRuntime(Integer runtime) {
        if (runtime == null || runtime <= 0) {
            return 0;
        }
        return runtime.shortValue();
    }

    /**
     * Extracts the first director from credits.crew where job = "Director".
     * Returns empty string if no director found (required field — admin must update).
     */
    private String extractDirector(TmdbMovieDetailDto detail) {
        if (detail.getCredits() == null || detail.getCredits().getCrew() == null) {
            return "";
        }
        return detail.getCredits().getCrew().stream()
                .filter(c -> DIRECTOR_JOB.equalsIgnoreCase(c.getJob()))
                .map(TmdbMovieDetailDto.TmdbCrewDto::getName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }

    /**
     * Extracts up to MAX_ACTORS cast members sorted by order.
     * Returns empty string if no cast found.
     */
    private String extractActors(TmdbMovieDetailDto detail) {
        if (detail.getCredits() == null || detail.getCredits().getCast() == null) {
            return "";
        }
        return detail.getCredits().getCast().stream()
                .filter(c -> c.getName() != null)
                .sorted((a, b) -> {
                    int orderA = a.getOrder() != null ? a.getOrder() : Integer.MAX_VALUE;
                    int orderB = b.getOrder() != null ? b.getOrder() : Integer.MAX_VALUE;
                    return Integer.compare(orderA, orderB);
                })
                .limit(MAX_ACTORS)
                .map(TmdbMovieDetailDto.TmdbCastDto::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Extracts the primary production country name.
     * Returns null if not available.
     */
    private String extractCountry(TmdbMovieDetailDto detail) {
        if (detail.getProductionCountries() == null || detail.getProductionCountries().isEmpty()) {
            return null;
        }
        return detail.getProductionCountries().get(0).getName();
    }

    /**
     * Extracts a YouTube trailer URL.
     *
     * Priority:
     * 1. YouTube + Trailer type + official=true
     * 2. YouTube + Trailer type (any)
     *
     * Returns null if no suitable trailer found — does not fail the import.
     */
    private String extractTrailerUrl(TmdbMovieDetailDto detail) {
        if (detail.getVideos() == null || detail.getVideos().getResults() == null) {
            return null;
        }

        List<TmdbMovieDetailDto.TmdbVideoDto> trailers = detail.getVideos().getResults().stream()
                .filter(v -> YOUTUBE_SITE.equalsIgnoreCase(v.getSite()) && TRAILER_TYPE.equalsIgnoreCase(v.getType()))
                .toList();

        if (trailers.isEmpty()) {
            return null;
        }

        // Prefer official trailer
        TmdbMovieDetailDto.TmdbVideoDto chosen = trailers.stream()
                .filter(v -> Boolean.TRUE.equals(v.getOfficial()))
                .findFirst()
                .orElse(trailers.get(0));

        return "https://www.youtube.com/watch?v=" + chosen.getKey();
    }

    /**
     * Extracts age rating (certification) from TMDB release_dates.
     *
     * Strategy:
     * 1. Look for US release entries with a non-blank certification.
     * 2. If not found, default to "NR".
     *
     * Missing certification does NOT fail the import.
     */
    private String extractAgeRating(TmdbMovieDetailDto detail) {
        if (detail.getReleaseDates() == null || detail.getReleaseDates().getResults() == null) {
            return DEFAULT_AGE_RATING;
        }

        return detail.getReleaseDates().getResults().stream()
                .filter(r -> CERTIFICATION_COUNTRY.equalsIgnoreCase(r.getCountry()))
                .findFirst()
                .flatMap(country -> {
                    if (country.getReleaseDates() == null) return Optional.empty();
                    return country.getReleaseDates().stream()
                            .map(TmdbMovieDetailDto.TmdbReleaseDateEntryDto::getCertification)
                            .filter(cert -> cert != null && !cert.isBlank())
                            .findFirst();
                })
                .orElse(DEFAULT_AGE_RATING);
    }

    /**
     * Determines initial MovieStatus for a newly imported movie based on release date.
     *
     * Assumption (per developer instruction):
     * - releaseDate > today → COMING_SOON
     * - releaseDate <= today → NOW_SHOWING
     *
     * This only applies to NEW movies. Re-import NEVER changes an existing movie's status.
     */
    private MovieStatus determineInitialStatus(LocalDate releaseDate) {
        if (releaseDate != null && !releaseDate.isAfter(LocalDate.now())) {
            return MovieStatus.NOW_SHOWING;
        }
        return MovieStatus.COMING_SOON;
    }
}
