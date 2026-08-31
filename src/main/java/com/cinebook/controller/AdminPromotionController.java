package com.cinebook.controller;

import com.cinebook.dto.request.CreatePromotionRequest;
import com.cinebook.dto.request.UpdatePromotionRequest;
import com.cinebook.dto.request.UpdatePromotionStatusRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.PromotionResponse;
import com.cinebook.enums.PromotionStatus;
import com.cinebook.service.PromotionService;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Promotion", description = "Administrator promotion and voucher management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/promotions")
@RequiredArgsConstructor
public class AdminPromotionController {

    private final PromotionService promotionService;

    @Operation(summary = "List all promotions for administration with search and filtering")
    @GetMapping
    public ResponseEntity<PageResponse<PromotionResponse>> getAdminPromotions(
            @RequestParam(name = "status", required = false) PromotionStatus status,
            @RequestParam(name = "q", required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<PromotionResponse> response = promotionService.getAdminPromotions(status, q, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get promotion detail by ID for administration")
    @GetMapping("/{id}")
    public ResponseEntity<PromotionResponse> getPromotionDetail(@PathVariable String id) {
        PromotionResponse response = promotionService.getPromotionDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Create a new promotion")
    @PostMapping
    public ResponseEntity<PromotionResponse> createPromotion(@Valid @RequestBody CreatePromotionRequest request) {
        PromotionResponse response = promotionService.createPromotion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update an existing promotion")
    @PutMapping("/{id}")
    public ResponseEntity<PromotionResponse> updatePromotion(
            @PathVariable String id,
            @Valid @RequestBody UpdatePromotionRequest request
    ) {
        PromotionResponse response = promotionService.updatePromotion(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update promotion status (ACTIVE / INACTIVE)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<PromotionResponse> updatePromotionStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdatePromotionStatusRequest request
    ) {
        PromotionResponse response = promotionService.updatePromotionStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
