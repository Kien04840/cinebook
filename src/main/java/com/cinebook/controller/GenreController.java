package com.cinebook.controller;

import com.cinebook.dto.response.GenreResponse;
import com.cinebook.service.GenreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Genre", description = "Public genre endpoints")
@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @Operation(summary = "Get list of all genres")
    @GetMapping
    public ResponseEntity<List<GenreResponse>> getAllGenres() {
        List<GenreResponse> response = genreService.getAllGenres();
        return ResponseEntity.ok(response);
    }
}

