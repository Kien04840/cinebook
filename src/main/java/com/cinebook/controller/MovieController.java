package com.cinebook.controller;

import com.cinebook.dto.response.MovieDetailResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.MovieStatus;
import com.cinebook.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Movie", description = "Public movie discovery endpoints")
@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @Operation(summary = "List publicly available movies with search, filter, pagination, and sorting")
    @GetMapping
    public ResponseEntity<PageResponse<MovieSummaryResponse>> getPublicMovies(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "genre", required = false) String genre,
            @RequestParam(name = "status", required = false) MovieStatus status,
            @PageableDefault(size = 20, sort = "releaseDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<MovieSummaryResponse> response = movieService.getPublicMovies(q, genre, status, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get public movie detail by ID")
    @GetMapping("/{id}")
    public ResponseEntity<MovieDetailResponse> getMovieDetail(@PathVariable String id) {
        MovieDetailResponse response = movieService.getMovieDetail(id);
        return ResponseEntity.ok(response);
    }
}

