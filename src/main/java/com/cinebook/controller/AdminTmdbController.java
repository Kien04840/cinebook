package com.cinebook.controller;

import com.cinebook.dto.response.TmdbGenreSyncResponse;
import com.cinebook.dto.response.TmdbMovieImportResponse;
import com.cinebook.service.TmdbImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for TMDB data import/synchronization.
 * Security is enforced by SecurityConfig: /api/v1/admin/** requires ROLE_ADMIN.
 */
@Tag(name = "Admin TMDB", description = "Administrator endpoints for TMDB data import and synchronization")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/tmdb")
@RequiredArgsConstructor
public class AdminTmdbController {

    private final TmdbImportService tmdbImportService;

    @Operation(
            summary = "Sync genres from TMDB",
            description = "Fetches the TMDB movie genre list and synchronizes it with CineBook genres. " +
                    "Creates new genres, updates changed names. Does not delete existing CineBook genres."
    )
    @PostMapping("/genres/sync")
    public ResponseEntity<TmdbGenreSyncResponse> syncGenres() {
        TmdbGenreSyncResponse response = tmdbImportService.syncGenres();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Import or update a movie from TMDB",
            description = "Imports a movie by its TMDB ID. If the movie does not exist, creates it. " +
                    "If it already exists, updates TMDB-sourced fields while preserving CineBook status and lifecycle fields. " +
                    "Idempotent: safe to call multiple times with the same tmdbId."
    )
    @PostMapping("/movies/{tmdbId}/import")
    public ResponseEntity<TmdbMovieImportResponse> importMovie(@PathVariable Long tmdbId) {
        TmdbMovieImportResponse response = tmdbImportService.importMovie(tmdbId);
        return ResponseEntity.ok(response);
    }
}
