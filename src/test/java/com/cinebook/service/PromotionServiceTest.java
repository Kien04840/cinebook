package com.cinebook.service;

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
import com.cinebook.service.impl.PromotionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Spy
    private PromotionMapper promotionMapper = new PromotionMapper();

    private PromotionServiceImpl promotionService;

    private Promotion testPromotion;

    @BeforeEach
    void setUp() {
        promotionService = new PromotionServiceImpl(promotionRepository, promotionMapper);

        testPromotion = new Promotion();
        testPromotion.setId("promo-1");
        testPromotion.setCode("SUMMER20");
        testPromotion.setName("Giảm 20% mùa hè");
        testPromotion.setDescription("Áp dụng cho mọi đơn hàng");
        testPromotion.setDiscountType(PromotionDiscountType.PERCENTAGE);
        testPromotion.setDiscountValue(new BigDecimal("20.00"));
        testPromotion.setMinOrderAmount(new BigDecimal("100000.00"));
        testPromotion.setMaxDiscountAmount(new BigDecimal("50000.00"));
        testPromotion.setStartAt(LocalDateTime.now().minusDays(1));
        testPromotion.setEndAt(LocalDateTime.now().plusDays(10));
        testPromotion.setUsageLimit(100);
        testPromotion.setUsedCount(10);
        testPromotion.setStatus(PromotionStatus.ACTIVE);
    }

    @Test
    @DisplayName("getAdminPromotions - Returns paginated promotion list")
    void testGetAdminPromotions() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Promotion> page = new PageImpl<>(List.of(testPromotion), pageable, 1);
        when(promotionRepository.findAdminPromotions(eq(PromotionStatus.ACTIVE), eq("SUMMER"), eq(pageable))).thenReturn(page);

        PageResponse<PromotionResponse> response = promotionService.getAdminPromotions(PromotionStatus.ACTIVE, "SUMMER", pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getCode()).isEqualTo("SUMMER20");
        assertThat(response.getContent().get(0).getRemainingUses()).isEqualTo(90);
    }

    @Test
    @DisplayName("getPromotionDetail - Success")
    void testGetPromotionDetail_Success() {
        when(promotionRepository.findById("promo-1")).thenReturn(Optional.of(testPromotion));

        PromotionResponse response = promotionService.getPromotionDetail("promo-1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("promo-1");
        assertThat(response.getCode()).isEqualTo("SUMMER20");
    }

    @Test
    @DisplayName("getPromotionDetail - Not found throws 404")
    void testGetPromotionDetail_NotFound_ThrowsException() {
        when(promotionRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.getPromotionDetail("non-existent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("createPromotion - Percentage promotion happy path")
    void testCreatePromotion_Percentage_Success() {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("autumn15")
                .name("Giảm 15% mùa thu")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("15.00"))
                .minOrderAmount(new BigDecimal("120000.00"))
                .maxDiscountAmount(new BigDecimal("40000.00"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .usageLimit(50)
                .build();

        when(promotionRepository.existsByCode("AUTUMN15")).thenReturn(false);
        when(promotionRepository.saveAndFlush(any(Promotion.class))).thenAnswer(i -> i.getArgument(0));

        PromotionResponse response = promotionService.createPromotion(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("AUTUMN15");
        assertThat(response.getDiscountType()).isEqualTo(PromotionDiscountType.PERCENTAGE);
        assertThat(response.getUsedCount()).isEqualTo(0);
        assertThat(response.getStatus()).isEqualTo(PromotionStatus.ACTIVE);
    }

    @Test
    @DisplayName("createPromotion - Fixed amount promotion happy path")
    void testCreatePromotion_FixedAmount_Success() {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("FIX50K")
                .name("Giảm 50.000đ")
                .discountType(PromotionDiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("50000.00"))
                .minOrderAmount(new BigDecimal("150000.00"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        when(promotionRepository.existsByCode("FIX50K")).thenReturn(false);
        when(promotionRepository.saveAndFlush(any(Promotion.class))).thenAnswer(i -> i.getArgument(0));

        PromotionResponse response = promotionService.createPromotion(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("FIX50K");
        assertThat(response.getDiscountType()).isEqualTo(PromotionDiscountType.FIXED_AMOUNT);
        assertThat(response.getDiscountValue()).isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("createPromotion - Duplicate code throws 409 Conflict")
    void testCreatePromotion_DuplicateCode_ThrowsConflict() {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("SUMMER20")
                .name("Duplicate")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10.00"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        when(promotionRepository.existsByCode("SUMMER20")).thenReturn(true);

        assertThatThrownBy(() -> promotionService.createPromotion(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("createPromotion - End date before start date throws 400 Bad Request")
    void testCreatePromotion_InvalidDates_ThrowsBadRequest() {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("INVALID_DATES")
                .name("Invalid")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10.00"))
                .startAt(LocalDateTime.now().plusDays(5))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();

        assertThatThrownBy(() -> promotionService.createPromotion(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ngày kết thúc phải sau ngày bắt đầu");
    }

    @Test
    @DisplayName("createPromotion - Percentage > 100 throws 400 Bad Request")
    void testCreatePromotion_PercentageGreaterThan100_ThrowsBadRequest() {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("OVER100")
                .name("Invalid 120%")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("120.00"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        assertThatThrownBy(() -> promotionService.createPromotion(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thể vượt quá 100%");
    }

    @Test
    @DisplayName("createPromotion - Fixed amount with maxDiscountAmount throws 400 Bad Request")
    void testCreatePromotion_FixedAmountWithMaxDiscount_ThrowsBadRequest() {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("FIX_BAD")
                .name("Invalid fixed with cap")
                .discountType(PromotionDiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("50000.00"))
                .maxDiscountAmount(new BigDecimal("30000.00"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        assertThatThrownBy(() -> promotionService.createPromotion(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không áp dụng mức giảm tối đa cho hình thức giảm giá theo số tiền cố định");
    }

    @Test
    @DisplayName("updatePromotion - Success")
    void testUpdatePromotion_Success() {
        UpdatePromotionRequest request = UpdatePromotionRequest.builder()
                .name("Updated Name")
                .description("Updated Description")
                .minOrderAmount(new BigDecimal("150000.00"))
                .maxDiscountAmount(new BigDecimal("60000.00"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(20))
                .usageLimit(200)
                .build();

        when(promotionRepository.findById("promo-1")).thenReturn(Optional.of(testPromotion));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(i -> i.getArgument(0));

        PromotionResponse response = promotionService.updatePromotion("promo-1", request);

        assertThat(response.getName()).isEqualTo("Updated Name");
        assertThat(response.getUsageLimit()).isEqualTo(200);
        assertThat(response.getRemainingUses()).isEqualTo(190);
    }

    @Test
    @DisplayName("updatePromotion - Cannot reduce usage limit below usedCount")
    void testUpdatePromotion_ReduceUsageLimitBelowUsedCount_ThrowsBadRequest() {
        UpdatePromotionRequest request = UpdatePromotionRequest.builder()
                .name("Updated Name")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(20))
                .usageLimit(5) // testPromotion.usedCount is 10
                .build();

        when(promotionRepository.findById("promo-1")).thenReturn(Optional.of(testPromotion));

        assertThatThrownBy(() -> promotionService.updatePromotion("promo-1", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thể nhỏ hơn số lượt đã sử dụng");
    }

    @Test
    @DisplayName("updatePromotionStatus - Toggle to INACTIVE and back to ACTIVE")
    void testUpdatePromotionStatus_Success() {
        when(promotionRepository.findById("promo-1")).thenReturn(Optional.of(testPromotion));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(i -> i.getArgument(0));

        UpdatePromotionStatusRequest deactivateReq = new UpdatePromotionStatusRequest(PromotionStatus.INACTIVE);
        PromotionResponse resp1 = promotionService.updatePromotionStatus("promo-1", deactivateReq);
        assertThat(resp1.getStatus()).isEqualTo(PromotionStatus.INACTIVE);

        UpdatePromotionStatusRequest activateReq = new UpdatePromotionStatusRequest(PromotionStatus.ACTIVE);
        PromotionResponse resp2 = promotionService.updatePromotionStatus("promo-1", activateReq);
        assertThat(resp2.getStatus()).isEqualTo(PromotionStatus.ACTIVE);
    }

    @Test
    @DisplayName("updatePromotionStatus - Cannot activate already expired promotion")
    void testUpdatePromotionStatus_ExpiredPromotion_ThrowsBadRequest() {
        testPromotion.setEndAt(LocalDateTime.now().minusMinutes(10));
        when(promotionRepository.findById("promo-1")).thenReturn(Optional.of(testPromotion));

        UpdatePromotionStatusRequest req = new UpdatePromotionStatusRequest(PromotionStatus.ACTIVE);

        assertThatThrownBy(() -> promotionService.updatePromotionStatus("promo-1", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể kích hoạt mã giảm giá đã hết hạn");
    }

    @Test
    @DisplayName("validatePromotionCode - Valid code preview")
    void testValidatePromotionCode_Valid() {
        when(promotionRepository.findByCode("SUMMER20")).thenReturn(Optional.of(testPromotion));

        ValidatePromotionResponse response = promotionService.validatePromotionCode("summer20", new BigDecimal("200000.00"));

        assertThat(response.isValid()).isTrue();
        assertThat(response.getCode()).isEqualTo("SUMMER20");
        assertThat(response.getGrossAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("40000.00")); // 20% of 200k = 40k <= 50k cap
        assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("160000.00"));
    }

    @Test
    @DisplayName("validatePromotionCode - Percentage capped at maxDiscountAmount")
    void testValidatePromotionCode_PercentageCapped() {
        when(promotionRepository.findByCode("SUMMER20")).thenReturn(Optional.of(testPromotion));

        ValidatePromotionResponse response = promotionService.validatePromotionCode("SUMMER20", new BigDecimal("400000.00"));

        assertThat(response.isValid()).isTrue();
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("50000.00")); // 20% of 400k = 80k, capped at 50k
        assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("350000.00"));
    }

    @Test
    @DisplayName("validatePromotionCode - Below minOrderAmount returns valid=false")
    void testValidatePromotionCode_BelowMinOrder() {
        when(promotionRepository.findByCode("SUMMER20")).thenReturn(Optional.of(testPromotion));

        ValidatePromotionResponse response = promotionService.validatePromotionCode("SUMMER20", new BigDecimal("80000.00")); // Min is 100k

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("Đơn hàng chưa đạt giá trị tối thiểu");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("80000.00"));
    }

    @Test
    @DisplayName("validatePromotionCode - Quota exhausted returns valid=false")
    void testValidatePromotionCode_QuotaExhausted() {
        testPromotion.setUsedCount(100); // usageLimit is 100
        when(promotionRepository.findByCode("SUMMER20")).thenReturn(Optional.of(testPromotion));

        ValidatePromotionResponse response = promotionService.validatePromotionCode("SUMMER20", new BigDecimal("200000.00"));

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("đã hết lượt sử dụng");
    }

    @Test
    @DisplayName("validatePromotionCode - Expired returns valid=false")
    void testValidatePromotionCode_Expired() {
        testPromotion.setEndAt(LocalDateTime.now().minusMinutes(5));
        when(promotionRepository.findByCode("SUMMER20")).thenReturn(Optional.of(testPromotion));

        ValidatePromotionResponse response = promotionService.validatePromotionCode("SUMMER20", new BigDecimal("200000.00"));

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("đã hết hạn");
    }

    @Test
    @DisplayName("validatePromotionCode - Inactive returns valid=false")
    void testValidatePromotionCode_Inactive() {
        testPromotion.setStatus(PromotionStatus.INACTIVE);
        when(promotionRepository.findByCode("SUMMER20")).thenReturn(Optional.of(testPromotion));

        ValidatePromotionResponse response = promotionService.validatePromotionCode("SUMMER20", new BigDecimal("200000.00"));

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("tạm khóa");
    }

    @Test
    @DisplayName("validatePromotionCode - Non-existent code returns valid=false")
    void testValidatePromotionCode_NotFound() {
        when(promotionRepository.findByCode("NON_EXISTENT")).thenReturn(Optional.empty());

        ValidatePromotionResponse response = promotionService.validatePromotionCode("NON_EXISTENT", new BigDecimal("200000.00"));

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("không tồn tại");
    }

    @Test
    @DisplayName("validatePromotionCode - Negative gross amount returns valid=false")
    void testValidatePromotionCode_NegativeGrossAmount() {
        ValidatePromotionResponse response = promotionService.validatePromotionCode("SUMMER20", new BigDecimal("-50000.00"));

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("không hợp lệ");
    }

    @Test
    @DisplayName("calculateDiscount - Fixed amount exceeding gross is capped at gross")
    void testCalculateDiscount_FixedAmountExceedingGross() {
        Promotion fixPromo = new Promotion();
        fixPromo.setDiscountType(PromotionDiscountType.FIXED_AMOUNT);
        fixPromo.setDiscountValue(new BigDecimal("150000.00"));

        BigDecimal discount = promotionService.calculateDiscount(fixPromo, new BigDecimal("100000.00"));
        assertThat(discount).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("calculateDiscount - 100% percentage discount")
    void testCalculateDiscount_100Percent() {
        Promotion fullPromo = new Promotion();
        fullPromo.setDiscountType(PromotionDiscountType.PERCENTAGE);
        fullPromo.setDiscountValue(new BigDecimal("100.00"));

        BigDecimal discount = promotionService.calculateDiscount(fullPromo, new BigDecimal("180000.00"));
        assertThat(discount).isEqualByComparingTo(new BigDecimal("180000.00"));
    }
}

