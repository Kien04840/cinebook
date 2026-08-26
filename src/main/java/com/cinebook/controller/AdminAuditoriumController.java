package com.cinebook.controller;

import com.cinebook.dto.request.BatchUpdateSeatTypeRequest;
import com.cinebook.dto.request.UpdateAuditoriumRequest;
import com.cinebook.dto.request.UpdateSeatStatusRequest;
import com.cinebook.dto.request.UpdateSeatTypeForSeatRequest;
import com.cinebook.dto.response.AuditoriumAvailabilityResponse;
import com.cinebook.dto.response.AuditoriumDetailResponse;
import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.SeatResponse;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.SeatService;
import com.cinebook.service.ShowtimeSchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Admin Auditorium", description = "Administrator auditorium and seat management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/auditoriums")
@RequiredArgsConstructor
public class AdminAuditoriumController {

    private final AuditoriumService auditoriumService;
    private final SeatService seatService;
    private final ShowtimeSchedulingService schedulingService;

    @Operation(summary = "Get auditorium detail including seats")
    @GetMapping("/{id}")
    public ResponseEntity<AuditoriumDetailResponse> getAuditoriumDetail(@PathVariable String id) {
        AuditoriumDetailResponse response = auditoriumService.getAuditoriumDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get auditorium availability and occupancy intervals on a date")
    @GetMapping("/{id}/availability")
    public ResponseEntity<AuditoriumAvailabilityResponse> getAuditoriumAvailability(
            @PathVariable String id,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        AuditoriumAvailabilityResponse response = schedulingService.getAuditoriumAvailability(id, date);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update an existing auditorium")
    @PutMapping("/{id}")
    public ResponseEntity<AuditoriumResponse> updateAuditorium(
            @PathVariable String id,
            @Valid @RequestBody UpdateAuditoriumRequest request
    ) {
        AuditoriumResponse response = auditoriumService.updateAuditorium(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Soft delete / mark auditorium as decommissioned")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAuditorium(@PathVariable String id) {
        auditoriumService.deleteAuditorium(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get all seats of an auditorium")
    @GetMapping("/{id}/seats")
    public ResponseEntity<List<SeatResponse>> getAuditoriumSeats(@PathVariable String id) {
        List<SeatResponse> response = seatService.getSeatsByAuditorium(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update seat type of a single seat")
    @PutMapping("/{auditoriumId}/seats/{seatId}/seat-type")
    public ResponseEntity<SeatResponse> updateSeatType(
            @PathVariable String auditoriumId,
            @PathVariable String seatId,
            @Valid @RequestBody UpdateSeatTypeForSeatRequest request
    ) {
        SeatResponse response = seatService.updateSeatType(seatId, request.getSeatTypeId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Batch update seat types for multiple seats in an auditorium")
    @PutMapping("/{auditoriumId}/seats/batch-seat-type")
    public ResponseEntity<List<SeatResponse>> batchUpdateSeatType(
            @PathVariable String auditoriumId,
            @Valid @RequestBody BatchUpdateSeatTypeRequest request
    ) {
        List<SeatResponse> response = seatService.batchUpdateSeatType(auditoriumId, request.getSeatIds(), request.getSeatTypeId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update seat status (ACTIVE / BROKEN)")
    @PatchMapping("/{auditoriumId}/seats/{seatId}/status")
    public ResponseEntity<SeatResponse> updateSeatStatus(
            @PathVariable String auditoriumId,
            @PathVariable String seatId,
            @Valid @RequestBody UpdateSeatStatusRequest request
    ) {
        SeatResponse response = seatService.updateSeatStatus(seatId, request.getStatus());
        return ResponseEntity.ok(response);
    }
}