package com.cinebook.controller;

import com.cinebook.dto.request.BatchUpdateSeatTypeRequest;
import com.cinebook.dto.request.UpdateAuditoriumRequest;
import com.cinebook.dto.request.UpdateSeatStatusRequest;
import com.cinebook.dto.request.UpdateSeatTypeForSeatRequest;
import com.cinebook.dto.response.AuditoriumDetailResponse;
import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.SeatResponse;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Auditorium", description = "Administrator auditorium and seat management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/auditoriums")
@RequiredArgsConstructor
public class AdminAuditoriumController {

    private final AuditoriumService auditoriumService;
    private final SeatService seatService;

    @Operation(summary = "Get auditorium detail including seats")
    @GetMapping("/{id}")
    public ResponseEntity<AuditoriumDetailResponse> getAuditoriumDetail(@PathVariable String id) {
        AuditoriumDetailResponse response = auditoriumService.getAuditoriumDetail(id);
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

    @Operation(summary = "Soft delete / mark auditorium as maintenance")
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