package com.cinebook.controller;

import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.CinemaDetailResponse;
import com.cinebook.dto.response.CinemaSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.CinemaService;
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

import java.util.List;

@Tag(name = "Cinema", description = "Public cinema and auditorium discovery endpoints")
@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;
    private final AuditoriumService auditoriumService;

    @Operation(summary = "List cinemas with optional city, status, search keyword, and pagination")
    @GetMapping
    public ResponseEntity<PageResponse<CinemaSummaryResponse>> getPublicCinemas(
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "status", required = false) CinemaStatus status,
            @RequestParam(name = "q", required = false) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<CinemaSummaryResponse> response = cinemaService.getPublicCinemas(city, status, q, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get cinema details with active auditoriums by ID")
    @GetMapping("/{id}")
    public ResponseEntity<CinemaDetailResponse> getCinemaDetail(@PathVariable String id) {
        CinemaDetailResponse response = cinemaService.getPublicCinemaDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List auditoriums of a specific cinema")
    @GetMapping("/{id}/auditoriums")
    public ResponseEntity<List<AuditoriumResponse>> getAuditoriumsByCinema(@PathVariable String id) {
        List<AuditoriumResponse> response = auditoriumService.getAuditoriumsByCinema(id);
        return ResponseEntity.ok(response);
    }
}