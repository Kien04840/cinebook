package com.cinebook.controller;

import com.cinebook.dto.request.*;
import com.cinebook.dto.response.*;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.service.ShowtimeSchedulingService;
import com.cinebook.service.ShowtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Admin Showtime", description = "Administrator showtime scheduling and management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/showtimes")
@RequiredArgsConstructor
public class AdminShowtimeController {

    private final ShowtimeService showtimeService;
    private final ShowtimeSchedulingService schedulingService;

    @Operation(summary = "List showtimes for administration with search and filtering")
    @GetMapping
    public ResponseEntity<PageResponse<ShowtimeSummaryResponse>> getAdminShowtimes(
            @RequestParam(name = "movieId", required = false) String movieId,
            @RequestParam(name = "cinemaId", required = false) String cinemaId,
            @RequestParam(name = "auditoriumId", required = false) String auditoriumId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "status", required = false) ShowtimeStatus status,
            @RequestParam(name = "format", required = false) ShowtimeFormat format,
            @RequestParam(name = "language", required = false) String language,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<ShowtimeSummaryResponse> response = showtimeService.getAdminShowtimes(
                movieId, cinemaId, auditoriumId, date, status, format, language, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get calendar schedule board for a cinema")
    @GetMapping("/calendar")
    public ResponseEntity<CalendarScheduleResponse> getCalendarSchedule(
            @RequestParam(name = "cinemaId") String cinemaId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        CalendarScheduleResponse response = schedulingService.getCalendarSchedule(cinemaId, from, to);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get scheduling configuration for a cinema")
    @GetMapping("/scheduling-config")
    public ResponseEntity<CinemaSchedulingConfigResponse> getCinemaSchedulingConfig(
            @RequestParam(name = "cinemaId") String cinemaId
    ) {
        CinemaSchedulingConfigResponse response = schedulingService.getCinemaSchedulingConfig(cinemaId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get auditorium occupancy and availability intervals on a date")
    @GetMapping("/auditorium-availability")
    public ResponseEntity<AuditoriumAvailabilityResponse> getAuditoriumAvailability(
            @RequestParam(name = "auditoriumId") String auditoriumId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        AuditoriumAvailabilityResponse response = schedulingService.getAuditoriumAvailability(auditoriumId, date);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get showtime detail for administration by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ShowtimeDetailResponse> getAdminShowtimeDetail(@PathVariable String id) {
        ShowtimeDetailResponse response = showtimeService.getAdminShowtimeDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Create a new showtime schedule (endTime is automatically derived from movie duration)")
    @PostMapping
    public ResponseEntity<ShowtimeDetailResponse> createShowtime(@Valid @RequestBody CreateShowtimeRequest request) {
        ShowtimeDetailResponse response = showtimeService.createShowtime(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Validate a proposed single showtime slot without persisting")
    @PostMapping("/validate")
    public ResponseEntity<ValidateShowtimeSlotResponse> validateSingleSlot(
            @Valid @RequestBody ValidateShowtimeSlotRequest request
    ) {
        ValidateShowtimeSlotResponse response = schedulingService.validateSingleSlot(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Suggest the next earliest available slot for a movie in an auditorium")
    @PostMapping("/suggest-next-slot")
    public ResponseEntity<SuggestShowtimeSlotResponse> suggestNextSlot(
            @Valid @RequestBody SuggestShowtimeSlotRequest request
    ) {
        SuggestShowtimeSlotResponse response = schedulingService.suggestNextSlot(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Preview rule-based auto-generation of showtime slots (transient)")
    @PostMapping("/generate/preview")
    public ResponseEntity<ShowtimeGenerationPreviewResponse> previewGeneration(
            @Valid @RequestBody ShowtimeGenerationRequest request
    ) {
        ShowtimeGenerationPreviewResponse response = schedulingService.previewGeneration(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Execute rule-based auto-generation and persist valid showtimes")
    @PostMapping("/generate")
    public ResponseEntity<ShowtimeGenerationResultResponse> generateShowtimes(
            @Valid @RequestBody ShowtimeGenerationRequest request
    ) {
        ShowtimeGenerationResultResponse response = schedulingService.generateShowtimes(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Copy showtime schedule from a source date to a target date")
    @PostMapping("/copy")
    public ResponseEntity<CopyScheduleResultResponse> copySchedule(
            @Valid @RequestBody CopyScheduleRequest request
    ) {
        CopyScheduleResultResponse response = schedulingService.copySchedule(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update an existing showtime schedule")
    @PutMapping("/{id}")
    public ResponseEntity<ShowtimeDetailResponse> updateShowtime(
            @PathVariable String id,
            @Valid @RequestBody UpdateShowtimeRequest request
    ) {
        ShowtimeDetailResponse response = showtimeService.updateShowtime(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete or cancel a showtime schedule based on booking transactions")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShowtime(@PathVariable String id) {
        showtimeService.deleteShowtime(id);
        return ResponseEntity.noContent().build();
    }
}
