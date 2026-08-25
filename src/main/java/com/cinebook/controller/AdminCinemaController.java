package com.cinebook.controller;

import com.cinebook.dto.request.CreateAuditoriumRequest;
import com.cinebook.dto.request.CreateCinemaRequest;
import com.cinebook.dto.request.UpdateCinemaRequest;
import com.cinebook.dto.response.AuditoriumDetailResponse;
import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.CinemaDetailResponse;
import com.cinebook.dto.response.CinemaSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.CinemaService;
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

import java.util.List;

@Tag(name = "Admin Cinema", description = "Administrator cinema management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/cinemas")
@RequiredArgsConstructor
public class AdminCinemaController {

    private final CinemaService cinemaService;
    private final AuditoriumService auditoriumService;

    @Operation(summary = "List cinemas for administration with search and includeDeleted filter")
    @GetMapping
    public ResponseEntity<PageResponse<CinemaSummaryResponse>> getAdminCinemas(
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "status", required = false) CinemaStatus status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<CinemaSummaryResponse> response = cinemaService.getAdminCinemas(city, status, q, includeDeleted, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get cinema detail for administration")
    @GetMapping("/{id}")
    public ResponseEntity<CinemaDetailResponse> getCinemaDetail(@PathVariable String id) {
        CinemaDetailResponse response = cinemaService.getAdminCinemaDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Create a new cinema")
    @PostMapping
    public ResponseEntity<CinemaDetailResponse> createCinema(@Valid @RequestBody CreateCinemaRequest request) {
        CinemaDetailResponse response = cinemaService.createCinema(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update an existing cinema")
    @PutMapping("/{id}")
    public ResponseEntity<CinemaDetailResponse> updateCinema(
            @PathVariable String id,
            @Valid @RequestBody UpdateCinemaRequest request
    ) {
        CinemaDetailResponse response = cinemaService.updateCinema(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Soft delete / close a cinema")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCinema(@PathVariable String id) {
        cinemaService.deleteCinema(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List auditoriums of a cinema for administration")
    @GetMapping("/{cinemaId}/auditoriums")
    public ResponseEntity<List<AuditoriumResponse>> getAuditoriumsByCinema(@PathVariable String cinemaId) {
        List<AuditoriumResponse> response = auditoriumService.getAuditoriumsByCinema(cinemaId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Create an auditorium and automatically generate its seat matrix")
    @PostMapping("/{cinemaId}/auditoriums")
    public ResponseEntity<AuditoriumDetailResponse> createAuditorium(
            @PathVariable String cinemaId,
            @Valid @RequestBody CreateAuditoriumRequest request
    ) {
        AuditoriumDetailResponse response = auditoriumService.createAuditorium(cinemaId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}