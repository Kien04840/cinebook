package com.cinebook.controller;

import com.cinebook.dto.request.CreateSeatTypeRequest;
import com.cinebook.dto.request.UpdateSeatTypeRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.SeatTypeResponse;
import com.cinebook.service.SeatTypeService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Seat Type", description = "Administrator seat type management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/seat-types")
@RequiredArgsConstructor
public class AdminSeatTypeController {

    private final SeatTypeService seatTypeService;

    @Operation(summary = "List all seat types for administration")
    @GetMapping
    public ResponseEntity<PageResponse<SeatTypeResponse>> getAdminSeatTypes(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<SeatTypeResponse> response = seatTypeService.getAdminSeatTypes(pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get seat type detail by ID")
    @GetMapping("/{id}")
    public ResponseEntity<SeatTypeResponse> getSeatTypeDetail(@PathVariable String id) {
        SeatTypeResponse response = seatTypeService.getSeatTypeDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Create a new seat type")
    @PostMapping
    public ResponseEntity<SeatTypeResponse> createSeatType(@Valid @RequestBody CreateSeatTypeRequest request) {
        SeatTypeResponse response = seatTypeService.createSeatType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update an existing seat type")
    @PutMapping("/{id}")
    public ResponseEntity<SeatTypeResponse> updateSeatType(
            @PathVariable String id,
            @Valid @RequestBody UpdateSeatTypeRequest request
    ) {
        SeatTypeResponse response = seatTypeService.updateSeatType(id, request);
        return ResponseEntity.ok(response);
    }
}