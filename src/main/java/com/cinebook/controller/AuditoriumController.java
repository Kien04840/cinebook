package com.cinebook.controller;

import com.cinebook.dto.response.AuditoriumDetailResponse;
import com.cinebook.dto.response.SeatResponse;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Auditorium", description = "Public auditorium details and seat layout endpoints")
@RestController
@RequestMapping("/api/v1/auditoriums")
@RequiredArgsConstructor
public class AuditoriumController {

    private final AuditoriumService auditoriumService;
    private final SeatService seatService;

    @Operation(summary = "Get auditorium details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<AuditoriumDetailResponse> getAuditoriumDetail(@PathVariable String id) {
        AuditoriumDetailResponse response = auditoriumService.getAuditoriumDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get seat map layout of an auditorium")
    @GetMapping("/{id}/seats")
    public ResponseEntity<List<SeatResponse>> getAuditoriumSeats(@PathVariable String id) {
        List<SeatResponse> response = seatService.getSeatsByAuditorium(id);
        return ResponseEntity.ok(response);
    }
}