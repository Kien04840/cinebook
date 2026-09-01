package com.cinebook.service;

import com.cinebook.dto.request.CreateMovieRequest;
import com.cinebook.dto.request.UpdateMovieRequest;
import com.cinebook.dto.response.MovieDetailResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.MovieStatus;
import org.springframework.data.domain.Pageable;

public interface MovieService {

    PageResponse<MovieSummaryResponse> getPublicMovies(
            String keyword,
            String genre,
            MovieStatus status,
            Pageable pageable
    );

    MovieDetailResponse getMovieDetail(String id);

    PageResponse<MovieSummaryResponse> getAdminMovies(
            String keyword,
            String genre,
            MovieStatus status,
            Boolean includeDeleted,
            Pageable pageable
    );

    MovieDetailResponse createMovie(CreateMovieRequest request);

    MovieDetailResponse updateMovie(String id, UpdateMovieRequest request);

    void deleteMovie(String id);

    com.cinebook.dto.response.MovieRecommendationResponse getMovieRecommendations(Integer limit);
}

