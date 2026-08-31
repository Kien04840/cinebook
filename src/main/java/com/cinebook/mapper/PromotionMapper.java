package com.cinebook.mapper;

import com.cinebook.dto.response.BookingPromotionResponse;
import com.cinebook.dto.response.PromotionResponse;
import com.cinebook.dto.response.ValidatePromotionResponse;
import com.cinebook.entity.BookingPromotion;
import com.cinebook.entity.Promotion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PromotionMapper {

    public PromotionResponse toPromotionResponse(Promotion promotion) {
        if (promotion == null) {
            return null;
        }

        Integer remainingUses = null;
        if (promotion.getUsageLimit() != null) {
            int used = promotion.getUsedCount() != null ? promotion.getUsedCount() : 0;
            remainingUses = Math.max(0, promotion.getUsageLimit() - used);
        }

        return PromotionResponse.builder()
                .id(promotion.getId())
                .code(promotion.getCode())
                .name(promotion.getName())
                .description(promotion.getDescription())
                .discountType(promotion.getDiscountType())
                .discountValue(promotion.getDiscountValue())
                .minOrderAmount(promotion.getMinOrderAmount())
                .maxDiscountAmount(promotion.getMaxDiscountAmount())
                .startAt(promotion.getStartAt())
                .endAt(promotion.getEndAt())
                .usageLimit(promotion.getUsageLimit())
                .usedCount(promotion.getUsedCount())
                .remainingUses(remainingUses)
                .status(promotion.getStatus())
                .createdAt(promotion.getCreatedAt())
                .updatedAt(promotion.getUpdatedAt())
                .build();
    }

    public ValidatePromotionResponse toValidatePromotionResponse(
            boolean valid,
            Promotion promotion,
            String rawCode,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            String message
    ) {
        BigDecimal finalAmount = grossAmount;
        if (valid && discountAmount != null) {
            finalAmount = grossAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        }

        return ValidatePromotionResponse.builder()
                .valid(valid)
                .code(promotion != null ? promotion.getCode() : rawCode)
                .name(promotion != null ? promotion.getName() : null)
                .discountType(promotion != null ? promotion.getDiscountType() : null)
                .discountValue(promotion != null ? promotion.getDiscountValue() : null)
                .grossAmount(grossAmount)
                .discountAmount(valid ? discountAmount : BigDecimal.ZERO)
                .finalAmount(finalAmount)
                .message(message)
                .build();
    }

    public BookingPromotionResponse toBookingPromotionResponse(BookingPromotion bookingPromotion) {
        if (bookingPromotion == null) {
            return null;
        }

        Promotion promotion = bookingPromotion.getPromotion();
        return BookingPromotionResponse.builder()
                .promotionId(promotion != null ? promotion.getId() : (bookingPromotion.getId() != null ? bookingPromotion.getId().getPromotionId() : null))
                .code(promotion != null ? promotion.getCode() : null)
                .name(promotion != null ? promotion.getName() : null)
                .discountAmount(bookingPromotion.getDiscountAmount())
                .build();
    }

    public BookingPromotionResponse toBookingPromotionResponse(Promotion promotion, BigDecimal discountAmount) {
        if (promotion == null) {
            return null;
        }

        return BookingPromotionResponse.builder()
                .promotionId(promotion.getId())
                .code(promotion.getCode())
                .name(promotion.getName())
                .discountAmount(discountAmount)
                .build();
    }
}

