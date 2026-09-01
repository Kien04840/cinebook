package com.cinebook.service.impl;

import com.cinebook.dto.request.CreatePromotionRequest;
import com.cinebook.dto.request.UpdatePromotionRequest;
import com.cinebook.dto.request.UpdatePromotionStatusRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.PromotionResponse;
import com.cinebook.dto.response.ValidatePromotionResponse;
import com.cinebook.entity.Promotion;
import com.cinebook.enums.PromotionDiscountType;
import com.cinebook.enums.PromotionStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.PromotionMapper;
import com.cinebook.repository.PromotionRepository;
import com.cinebook.service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionMapper promotionMapper;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getPublicPromotions(Pageable pageable) {
        Page<Promotion> page = promotionRepository.findByStatus(PromotionStatus.ACTIVE, pageable);
        return PageResponse.of(page, promotionMapper::toPromotionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getAdminPromotions(PromotionStatus status, String keyword, Pageable pageable) {
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Promotion> page = promotionRepository.findAdminPromotions(status, trimmedKeyword, pageable);
        return PageResponse.of(page, promotionMapper::toPromotionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionDetail(String id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi với id: " + id));
        return promotionMapper.toPromotionResponse(promotion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionResponse createPromotion(CreatePromotionRequest request) {
        String code = request.getCode().trim().toUpperCase();

        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu.");
        }

        if (request.getDiscountType() == PromotionDiscountType.PERCENTAGE) {
            if (request.getDiscountValue().compareTo(HUNDRED) > 0) {
                throw new BadRequestException("Phần trăm giảm giá không thể vượt quá 100%.");
            }
        } else if (request.getDiscountType() == PromotionDiscountType.FIXED_AMOUNT) {
            if (request.getMaxDiscountAmount() != null) {
                throw new BadRequestException("Không áp dụng mức giảm tối đa cho hình thức giảm giá theo số tiền cố định.");
            }
        }

        if (promotionRepository.existsByCode(code)) {
            throw new ConflictException("Mã khuyến mãi đã tồn tại: " + code);
        }

        Promotion promotion = new Promotion();
        promotion.setId(UUID.randomUUID().toString());
        promotion.setCode(code);
        promotion.setName(request.getName().trim());
        promotion.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        promotion.setDiscountType(request.getDiscountType());
        promotion.setDiscountValue(request.getDiscountValue().setScale(2, RoundingMode.HALF_UP));
        promotion.setMinOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount().setScale(2, RoundingMode.HALF_UP) : null);
        promotion.setMaxDiscountAmount(request.getMaxDiscountAmount() != null ? request.getMaxDiscountAmount().setScale(2, RoundingMode.HALF_UP) : null);
        promotion.setStartAt(request.getStartAt());
        promotion.setEndAt(request.getEndAt());
        promotion.setUsageLimit(request.getUsageLimit());
        promotion.setUsedCount(0);
        promotion.setStatus(request.getStatus() != null ? request.getStatus() : PromotionStatus.ACTIVE);

        try {
            Promotion savedPromotion = promotionRepository.saveAndFlush(promotion);
            log.info("Created promotion: id={}, code={}, discountType={}", savedPromotion.getId(), savedPromotion.getCode(), savedPromotion.getDiscountType());
            return promotionMapper.toPromotionResponse(savedPromotion);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate promotion code creation attempt for code {}: {}", code, ex.getMessage());
            throw new ConflictException("Mã khuyến mãi đã tồn tại: " + code);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionResponse updatePromotion(String id, UpdatePromotionRequest request) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi với id: " + id));

        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu.");
        }

        if (promotion.getDiscountType() == PromotionDiscountType.FIXED_AMOUNT && request.getMaxDiscountAmount() != null) {
            throw new BadRequestException("Không áp dụng mức giảm tối đa cho hình thức giảm giá theo số tiền cố định.");
        }

        if (request.getUsageLimit() != null && request.getUsageLimit() < promotion.getUsedCount()) {
            throw new BadRequestException("Hạn mức sử dụng mới (" + request.getUsageLimit() + ") không thể nhỏ hơn số lượt đã sử dụng (" + promotion.getUsedCount() + ").");
        }

        promotion.setName(request.getName().trim());
        promotion.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        promotion.setMinOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount().setScale(2, RoundingMode.HALF_UP) : null);
        promotion.setMaxDiscountAmount(request.getMaxDiscountAmount() != null ? request.getMaxDiscountAmount().setScale(2, RoundingMode.HALF_UP) : null);
        promotion.setStartAt(request.getStartAt());
        promotion.setEndAt(request.getEndAt());
        promotion.setUsageLimit(request.getUsageLimit());

        Promotion updated = promotionRepository.save(promotion);
        log.info("Updated promotion: id={}, code={}", updated.getId(), updated.getCode());
        return promotionMapper.toPromotionResponse(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionResponse updatePromotionStatus(String id, UpdatePromotionStatusRequest request) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi với id: " + id));

        if (request.getStatus() == PromotionStatus.ACTIVE) {
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(promotion.getEndAt()) || now.isEqual(promotion.getEndAt())) {
                throw new BadRequestException("Không thể kích hoạt mã giảm giá đã hết hạn sử dụng.");
            }
        }

        promotion.setStatus(request.getStatus());
        Promotion updated = promotionRepository.save(promotion);
        log.info("Updated promotion status: id={}, code={}, status={}", updated.getId(), updated.getCode(), updated.getStatus());
        return promotionMapper.toPromotionResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public ValidatePromotionResponse validatePromotionCode(String code, BigDecimal grossAmount) {
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) < 0) {
            return promotionMapper.toValidatePromotionResponse(false, null, code, grossAmount != null ? grossAmount : BigDecimal.ZERO, BigDecimal.ZERO, "Số tiền đơn hàng không hợp lệ.");
        }

        if (!StringUtils.hasText(code)) {
            return promotionMapper.toValidatePromotionResponse(false, null, code, grossAmount, BigDecimal.ZERO, "Mã giảm giá không được để trống.");
        }

        String normalizedCode = code.trim().toUpperCase();
        Optional<Promotion> promoOpt = promotionRepository.findByCode(normalizedCode);

        if (promoOpt.isEmpty()) {
            return promotionMapper.toValidatePromotionResponse(false, null, normalizedCode, grossAmount, BigDecimal.ZERO, "Mã giảm giá không tồn tại.");
        }

        Promotion promotion = promoOpt.get();
        LocalDateTime now = LocalDateTime.now();

        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            return promotionMapper.toValidatePromotionResponse(false, promotion, normalizedCode, grossAmount, BigDecimal.ZERO, "Mã giảm giá hiện đang tạm khóa hoặc không hoạt động.");
        }

        if (now.isBefore(promotion.getStartAt())) {
            return promotionMapper.toValidatePromotionResponse(false, promotion, normalizedCode, grossAmount, BigDecimal.ZERO, "Mã giảm giá chưa đến thời gian áp dụng.");
        }

        if (now.isAfter(promotion.getEndAt()) || now.isEqual(promotion.getEndAt())) {
            return promotionMapper.toValidatePromotionResponse(false, promotion, normalizedCode, grossAmount, BigDecimal.ZERO, "Mã giảm giá đã hết hạn sử dụng.");
        }

        if (promotion.getMinOrderAmount() != null && grossAmount.compareTo(promotion.getMinOrderAmount()) < 0) {
            return promotionMapper.toValidatePromotionResponse(false, promotion, normalizedCode, grossAmount, BigDecimal.ZERO,
                    "Đơn hàng chưa đạt giá trị tối thiểu (" + promotion.getMinOrderAmount() + " VND) để áp dụng mã giảm giá.");
        }

        if (promotion.getUsageLimit() != null && promotion.getUsedCount() >= promotion.getUsageLimit()) {
            return promotionMapper.toValidatePromotionResponse(false, promotion, normalizedCode, grossAmount, BigDecimal.ZERO, "Mã giảm giá đã hết lượt sử dụng.");
        }

        BigDecimal discountAmount = calculateDiscount(promotion, grossAmount);
        return promotionMapper.toValidatePromotionResponse(true, promotion, normalizedCode, grossAmount, discountAmount, "Áp dụng mã giảm giá thành công.");
    }

    @Override
    public BigDecimal calculateDiscount(Promotion promotion, BigDecimal grossAmount) {
        if (promotion == null || grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal discount;
        if (promotion.getDiscountType() == PromotionDiscountType.PERCENTAGE) {
            BigDecimal rawDiscount = grossAmount.multiply(promotion.getDiscountValue())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);

            if (promotion.getMaxDiscountAmount() != null && rawDiscount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
                rawDiscount = promotion.getMaxDiscountAmount();
            }
            discount = rawDiscount.min(grossAmount);
        } else {
            discount = promotion.getDiscountValue().min(grossAmount);
        }

        return discount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}

