package com.cinebook.controller;

import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.SeatResponse;
import com.cinebook.dto.response.ShowtimeDetailResponse;
import com.cinebook.dto.response.ShowtimeSummaryResponse;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.service.SeatService;
import com.cinebook.service.ShowtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Showtime", description = "Public showtime discovery and seat layout endpoints")
@RestController
@RequestMapping("/api/v1/showtimes")
@RequiredArgsConstructor
public class ShowtimeController {

    private final ShowtimeService showtimeService;
    private final SeatService seatService;

    @Operation(summary = "List publicly available showtimes with optional filters")
    @GetMapping
    public ResponseEntity<PageResponse<ShowtimeSummaryResponse>> getPublicShowtimes(
            @RequestParam(name = "movieId", required = false) String movieId,
            @RequestParam(name = "cinemaId", required = false) String cinemaId,
            @RequestParam(name = "auditoriumId", required = false) String auditoriumId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "format", required = false) ShowtimeFormat format,
            @RequestParam(name = "language", required = false) String language,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<ShowtimeSummaryResponse> response = showtimeService.getPublicShowtimes(
                movieId, cinemaId, auditoriumId, date, format, language, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get public showtime details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ShowtimeDetailResponse> getPublicShowtimeDetail(@PathVariable String id) {
        ShowtimeDetailResponse response = showtimeService.getPublicShowtimeDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get seat map layout for a specific showtime")
    @GetMapping("/{id}/seats")
    public ResponseEntity<List<SeatResponse>> getShowtimeSeats(@PathVariable String id) {
        ShowtimeDetailResponse showtime = showtimeService.getPublicShowtimeDetail(id);
        List<SeatResponse> response = seatService.getSeatsByAuditorium(showtime.getAuditorium().getId());
        return ResponseEntity.ok(response);
    }
}