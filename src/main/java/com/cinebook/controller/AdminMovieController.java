package com.cinebook.controller;

import com.cinebook.dto.request.CreateMovieRequest;
import com.cinebook.dto.request.UpdateMovieRequest;
import com.cinebook.dto.response.MovieDetailResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.MovieStatus;
import com.cinebook.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Movie", description = "Administrator movie management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/movies")
@RequiredArgsConstructor
public class AdminMovieController {

    private final MovieService movieService;

    @Operation(summary = "List all movies for administration")
    @GetMapping
    public ResponseEntity<PageResponse<MovieSummaryResponse>> getAdminMovies(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "genre", required = false) String genre,
            @RequestParam(name = "status", required = false) MovieStatus status,
            @RequestParam(name = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<MovieSummaryResponse> response = movieService.getAdminMovies(q, genre, status, includeDeleted, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Create a new movie")
    @PostMapping
    public ResponseEntity<MovieDetailResponse> createMovie(@Valid @RequestBody CreateMovieRequest request) {
        MovieDetailResponse response = movieService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update an existing movie")
    @PutMapping("/{id}")
    public ResponseEntity<MovieDetailResponse> updateMovie(
            @PathVariable String id,
            @Valid @RequestBody UpdateMovieRequest request
    ) {
        MovieDetailResponse response = movieService.updateMovie(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Soft delete / stop displaying a movie")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMovie(@PathVariable String id) {
        movieService.deleteMovie(id);
        return ResponseEntity.noContent().build();
    }
}

