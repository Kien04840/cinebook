package com.cinebook.controller;

import com.cinebook.dto.response.SeatTypeResponse;
import com.cinebook.service.SeatTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Seat Type", description = "Public seat types endpoint")
@RestController
@RequestMapping("/api/v1/seat-types")
@RequiredArgsConstructor
public class SeatTypeController {

    private final SeatTypeService seatTypeService;

    @Operation(summary = "List all active seat types")
    @GetMapping
    public ResponseEntity<List<SeatTypeResponse>> getActiveSeatTypes() {
        List<SeatTypeResponse> response = seatTypeService.getAllActiveSeatTypes();
        return ResponseEntity.ok(response);
    }
}