package com.cinebook.service.impl;

import com.cinebook.dto.request.CreateMovieRequest;
import com.cinebook.dto.request.UpdateMovieRequest;
import com.cinebook.dto.response.MovieDetailResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.entity.Genre;
import com.cinebook.entity.Movie;
import com.cinebook.entity.MovieGenre;
import com.cinebook.entity.MovieGenreId;
import com.cinebook.enums.MovieStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.MovieMapper;
import com.cinebook.repository.GenreRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.specification.MovieSpecification;
import com.cinebook.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final com.cinebook.repository.BookingRepository bookingRepository;
    private final MovieMapper movieMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MovieSummaryResponse> getPublicMovies(
            String keyword,
            String genre,
            MovieStatus status,
            Pageable pageable
    ) {
        Specification<Movie> spec = Specification.where(MovieSpecification.isPubliclyVisible());

        if (status != null) {
            spec = spec.and(MovieSpecification.hasStatus(status));
        }

        if (StringUtils.hasText(keyword)) {
            spec = spec.and(MovieSpecification.searchKeyword(keyword));
        }

        if (StringUtils.hasText(genre)) {
            spec = spec.and(MovieSpecification.hasGenre(genre));
        }

        Page<Movie> moviePage = movieRepository.findAll(spec, pageable);
        return PageResponse.of(moviePage, movieMapper::toMovieSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public MovieDetailResponse getMovieDetail(String id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));

        if (movie.getDeletedAt() != null || movie.getStatus() == MovieStatus.HIDDEN) {
            throw new ResourceNotFoundException("Movie not found with id: " + id);
        }

        return movieMapper.toMovieDetailResponse(movie);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MovieSummaryResponse> getAdminMovies(
            String keyword,
            String genre,
            MovieStatus status,
            Boolean includeDeleted,
            Pageable pageable
    ) {
        Specification<Movie> spec = (root, query, cb) -> cb.conjunction();

        if (includeDeleted == null || !includeDeleted) {
            spec = spec.and(MovieSpecification.isNotDeleted());
        }

        if (status != null) {
            spec = spec.and(MovieSpecification.hasStatus(status));
        }

        if (StringUtils.hasText(keyword)) {
            spec = spec.and(MovieSpecification.searchKeyword(keyword));
        }

        if (StringUtils.hasText(genre)) {
            spec = spec.and(MovieSpecification.hasGenre(genre));
        }

        Page<Movie> moviePage = movieRepository.findAll(spec, pageable);
        return PageResponse.of(moviePage, movieMapper::toMovieSummaryResponse);
    }

    @Override
    @Transactional
    public MovieDetailResponse createMovie(CreateMovieRequest request) {
        if (request.getTmdbId() != null && movieRepository.existsByTmdbId(request.getTmdbId())) {
            throw new ConflictException("Movie with TMDB ID " + request.getTmdbId() + " already exists");
        }

        Set<Genre> genres = new HashSet<>();
        if (request.getGenreIds() != null && !request.getGenreIds().isEmpty()) {
            List<Genre> foundGenres = genreRepository.findAllById(request.getGenreIds());
            if (foundGenres.size() != request.getGenreIds().size()) {
                throw new BadRequestException("One or more genre IDs do not exist");
            }
            genres.addAll(foundGenres);
        }

        Movie movie = new Movie();
        movie.setTmdbId(request.getTmdbId());
        movie.setTitle(request.getTitle().trim());
        movie.setOriginalTitle(request.getOriginalTitle() != null ? request.getOriginalTitle().trim() : null);
        movie.setOverview(request.getOverview().trim());
        movie.setDurationMinutes(request.getDurationMinutes());
        movie.setDirector(request.getDirector().trim());
        movie.setActors(request.getActors().trim());
        movie.setCountry(request.getCountry());
        movie.setLanguage(request.getLanguage());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setAgeRating(request.getAgeRating().trim());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setBackdropUrl(request.getBackdropUrl());
        movie.setTrailerUrl(request.getTrailerUrl());
        movie.setStatus(request.getStatus());

        Movie savedMovie = movieRepository.save(movie);

        for (Genre genre : genres) {
            MovieGenre movieGenre = new MovieGenre();
            movieGenre.setId(new MovieGenreId(savedMovie.getId(), genre.getId()));
            movieGenre.setMovie(savedMovie);
            movieGenre.setGenre(genre);
            savedMovie.getMovieGenres().add(movieGenre);
        }

        savedMovie = movieRepository.save(savedMovie);
        return movieMapper.toMovieDetailResponse(savedMovie);
    }

    @Override
    @Transactional
    public MovieDetailResponse updateMovie(String id, UpdateMovieRequest request) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));

        if (request.getTmdbId() != null && !request.getTmdbId().equals(movie.getTmdbId())) {
            if (movieRepository.existsByTmdbId(request.getTmdbId())) {
                throw new ConflictException("Movie with TMDB ID " + request.getTmdbId() + " already exists");
            }
        }

        if (request.getGenreIds() != null) {
            List<Genre> newGenres = genreRepository.findAllById(request.getGenreIds());
            if (newGenres.size() != request.getGenreIds().size()) {
                throw new BadRequestException("One or more genre IDs do not exist");
            }

            Set<String> targetGenreIds = request.getGenreIds();
            movie.getMovieGenres().removeIf(mg -> !targetGenreIds.contains(mg.getGenre().getId()));

            Set<String> existingGenreIds = movie.getMovieGenres().stream()
                    .map(mg -> mg.getGenre().getId())
                    .collect(Collectors.toSet());

            for (Genre g : newGenres) {
                if (!existingGenreIds.contains(g.getId())) {
                    MovieGenre mg = new MovieGenre();
                    mg.setId(new MovieGenreId(movie.getId(), g.getId()));
                    mg.setMovie(movie);
                    mg.setGenre(g);
                    movie.getMovieGenres().add(mg);
                }
            }
        }

        movie.setTmdbId(request.getTmdbId());
        movie.setTitle(request.getTitle().trim());
        movie.setOriginalTitle(request.getOriginalTitle() != null ? request.getOriginalTitle().trim() : null);
        movie.setOverview(request.getOverview().trim());
        movie.setDurationMinutes(request.getDurationMinutes());
        movie.setDirector(request.getDirector().trim());
        movie.setActors(request.getActors().trim());
        movie.setCountry(request.getCountry());
        movie.setLanguage(request.getLanguage());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setAgeRating(request.getAgeRating().trim());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setBackdropUrl(request.getBackdropUrl());
        movie.setTrailerUrl(request.getTrailerUrl());
        movie.setStatus(request.getStatus());

        Movie updatedMovie = movieRepository.save(movie);
        return movieMapper.toMovieDetailResponse(updatedMovie);
    }

    @Override
    @Transactional
    public void deleteMovie(String id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));

        movie.setDeletedAt(LocalDateTime.now());
        movie.setStatus(MovieStatus.HIDDEN);
        movieRepository.save(movie);
    }

    @Override
    @Transactional(readOnly = true)
    public com.cinebook.dto.response.MovieRecommendationResponse getMovieRecommendations(Integer limit) {
        int maxResults = (limit != null && limit > 0 && limit <= 20) ? limit : 6;
        String currentUserId = null;
        try {
            currentUserId = com.cinebook.util.SecurityUtils.getCurrentUserId();
        } catch (Exception ignored) {
            // Unauthenticated user
        }

        List<com.cinebook.entity.Booking> userBookings = (currentUserId != null)
                ? bookingRepository.findByUserId(currentUserId)
                : List.of();

        List<com.cinebook.entity.Booking> relevantBookings = userBookings.stream()
                .filter(b -> b.getBookingStatus() == com.cinebook.enums.BookingStatus.PAID
                        || b.getBookingStatus() == com.cinebook.enums.BookingStatus.REFUNDED)
                .toList();

        Specification<Movie> activeSpec = Specification.where(MovieSpecification.isPubliclyVisible());
        List<Movie> candidateMovies = movieRepository.findAll(activeSpec);

        if (candidateMovies.isEmpty()) {
            return com.cinebook.dto.response.MovieRecommendationResponse.builder()
                    .explanation("Chưa có phim phù hợp")
                    .favoriteGenres(List.of())
                    .movies(List.of())
                    .build();
        }

        if (relevantBookings.isEmpty()) {
            List<MovieSummaryResponse> fallbackMovies = candidateMovies.stream()
                    .sorted((m1, m2) -> {
                        if (m1.getStatus() == MovieStatus.NOW_SHOWING && m2.getStatus() != MovieStatus.NOW_SHOWING) return -1;
                        if (m1.getStatus() != MovieStatus.NOW_SHOWING && m2.getStatus() == MovieStatus.NOW_SHOWING) return 1;
                        return m2.getReleaseDate().compareTo(m1.getReleaseDate());
                    })
                    .limit(maxResults)
                    .map(movieMapper::toMovieSummaryResponse)
                    .toList();

            return com.cinebook.dto.response.MovieRecommendationResponse.builder()
                    .explanation("Phim nổi bật đang chiếu tại rạp CineBook")
                    .favoriteGenres(List.of())
                    .movies(fallbackMovies)
                    .build();
        }

        Set<String> watchedMovieIds = new HashSet<>();
        java.util.Map<String, Integer> genreFrequency = new java.util.HashMap<>();

        for (com.cinebook.entity.Booking b : relevantBookings) {
            if (b.getShowtime() != null && b.getShowtime().getMovie() != null) {
                Movie movie = b.getShowtime().getMovie();
                watchedMovieIds.add(movie.getId());
                if (movie.getMovieGenres() != null) {
                    for (MovieGenre mg : movie.getMovieGenres()) {
                        if (mg.getGenre() != null) {
                            String gName = mg.getGenre().getName();
                            genreFrequency.put(gName, genreFrequency.getOrDefault(gName, 0) + 1);
                        }
                    }
                }
            }
        }

        List<String> topFavoriteGenres = genreFrequency.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(3)
                .map(java.util.Map.Entry::getKey)
                .toList();

        class ScoredMovie {
            final Movie movie;
            final double score;

            ScoredMovie(Movie movie, double score) {
                this.movie = movie;
                this.score = score;
            }
        }

        List<ScoredMovie> scoredList = new java.util.ArrayList<>();
        for (Movie m : candidateMovies) {
            double score = 0.0;

            if (m.getMovieGenres() != null) {
                for (MovieGenre mg : m.getMovieGenres()) {
                    if (mg.getGenre() != null) {
                        int freq = genreFrequency.getOrDefault(mg.getGenre().getName(), 0);
                        score += freq * 15.0;
                    }
                }
            }

            if (m.getStatus() == MovieStatus.NOW_SHOWING) {
                score += 20.0;
            } else if (m.getStatus() == MovieStatus.COMING_SOON) {
                score += 5.0;
            }

            if (watchedMovieIds.contains(m.getId())) {
                score -= 10.0;
            }

            scoredList.add(new ScoredMovie(m, score));
        }

        List<MovieSummaryResponse> recommended = scoredList.stream()
                .sorted((s1, s2) -> {
                    int scoreCmp = Double.compare(s2.score, s1.score);
                    if (scoreCmp != 0) return scoreCmp;
                    return s2.movie.getReleaseDate().compareTo(s1.movie.getReleaseDate());
                })
                .limit(maxResults)
                .map(sm -> movieMapper.toMovieSummaryResponse(sm.movie))
                .toList();

        String explanation;
        if (!topFavoriteGenres.isEmpty()) {
            explanation = "Dựa trên sở thích xem phim " + String.join(", ", topFavoriteGenres) + " của bạn";
        } else {
            explanation = "Gợi ý phim dành riêng cho bạn";
        }

        return com.cinebook.dto.response.MovieRecommendationResponse.builder()
                .explanation(explanation)
                .favoriteGenres(topFavoriteGenres)
                .movies(recommended)
                .build();
    }
}
