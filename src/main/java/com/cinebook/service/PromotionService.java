package com.cinebook.service;

import com.cinebook.dto.request.CreatePromotionRequest;
import com.cinebook.dto.request.UpdatePromotionRequest;
import com.cinebook.dto.request.UpdatePromotionStatusRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.PromotionResponse;
import com.cinebook.dto.response.ValidatePromotionResponse;
import com.cinebook.entity.Promotion;
import com.cinebook.enums.PromotionStatus;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface PromotionService {

    PageResponse<PromotionResponse> getAdminPromotions(PromotionStatus status, String keyword, Pageable pageable);

    PromotionResponse getPromotionDetail(String id);

    PromotionResponse createPromotion(CreatePromotionRequest request);

    PromotionResponse updatePromotion(String id, UpdatePromotionRequest request);

    PromotionResponse updatePromotionStatus(String id, UpdatePromotionStatusRequest request);

    ValidatePromotionResponse validatePromotionCode(String code, BigDecimal grossAmount);

    BigDecimal calculateDiscount(Promotion promotion, BigDecimal grossAmount);
}

