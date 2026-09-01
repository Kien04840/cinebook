package com.cinebook.controller;

import com.cinebook.dto.response.ValidatePromotionResponse;
import com.cinebook.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "Promotion", description = "Customer promotion preview and validation endpoints")
@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @Operation(summary = "List active public promotions for customers")
    @GetMapping
    public ResponseEntity<com.cinebook.dto.response.PageResponse<com.cinebook.dto.response.PromotionResponse>> getPublicPromotions(
            @org.springframework.data.web.PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable
    ) {
        com.cinebook.dto.response.PageResponse<com.cinebook.dto.response.PromotionResponse> response = promotionService.getPublicPromotions(pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Validate and preview a promotion code discount against a gross amount")
    @GetMapping("/validate")
    public ResponseEntity<ValidatePromotionResponse> validatePromotion(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "grossAmount") BigDecimal grossAmount
    ) {
        ValidatePromotionResponse response = promotionService.validatePromotionCode(code, grossAmount);
        return ResponseEntity.ok(response);
    }
}

