package com.cinebook.service.impl;

import com.cinebook.dto.request.CreateSeatTypeRequest;
import com.cinebook.dto.request.UpdateSeatTypeRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.SeatTypeResponse;
import com.cinebook.entity.SeatType;
import com.cinebook.enums.SeatTypeStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.SeatTypeMapper;
import com.cinebook.repository.SeatRepository;
import com.cinebook.repository.SeatTypeRepository;
import com.cinebook.service.SeatTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatTypeServiceImpl implements SeatTypeService {

    public static final Set<String> ALLOWED_COLOR_TOKENS = Set.of(
            "slate", "amber", "rose", "indigo", "emerald", "purple", "cyan", "pink"
    );

    public static final Set<String> ALLOWED_ICONS = Set.of(
            "armchair", "crown", "heart", "star", "sofa", "sparkles", "shield", "gem"
    );

    private final SeatTypeRepository seatTypeRepository;
    private final SeatRepository seatRepository;
    private final SeatTypeMapper seatTypeMapper;

    private void validatePresentationMetadata(String colorToken, String icon) {
        if (StringUtils.hasText(colorToken) && !ALLOWED_COLOR_TOKENS.contains(colorToken.trim().toLowerCase())) {
            throw new BadRequestException("Mã màu không hợp lệ: '" + colorToken + "'. Các màu được phép: " + ALLOWED_COLOR_TOKENS);
        }
        if (StringUtils.hasText(icon) && !ALLOWED_ICONS.contains(icon.trim().toLowerCase())) {
            throw new BadRequestException("Biểu tượng không hợp lệ: '" + icon + "'. Các biểu tượng được phép: " + ALLOWED_ICONS);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatTypeResponse> getAllActiveSeatTypes() {
        return seatTypeRepository.findByStatus(SeatTypeStatus.ACTIVE).stream()
                .map(seatTypeMapper::toSeatTypeResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SeatTypeResponse> getAdminSeatTypes(Pageable pageable) {
        Page<SeatType> page = seatTypeRepository.findAll(pageable);
        return PageResponse.of(page, seatTypeMapper::toSeatTypeResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SeatTypeResponse getSeatTypeDetail(String id) {
        SeatType seatType = seatTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SeatType not found with id: " + id));
        return seatTypeMapper.toSeatTypeResponse(seatType);
    }

    @Override
    @Transactional
    public SeatTypeResponse createSeatType(CreateSeatTypeRequest request) {
        String code = request.getCode().trim().toUpperCase();
        String name = request.getName().trim();

        if (seatTypeRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Loại ghế với mã '" + code + "' đã tồn tại");
        }

        if (seatTypeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Loại ghế với tên '" + name + "' đã tồn tại");
        }

        validatePresentationMetadata(request.getColorToken(), request.getIcon());

        SeatType seatType = new SeatType();
        seatType.setCode(code);
        seatType.setName(name);
        seatType.setPriceModifier(request.getPriceModifier());
        seatType.setCapacity(request.getCapacity() != null ? request.getCapacity() : (short) 1);
        seatType.setColorToken(StringUtils.hasText(request.getColorToken()) ? request.getColorToken().trim().toLowerCase() : "slate");
        seatType.setIcon(StringUtils.hasText(request.getIcon()) ? request.getIcon().trim().toLowerCase() : "armchair");
        seatType.setDescription(request.getDescription());
        seatType.setStatus(request.getStatus() != null ? request.getStatus() : SeatTypeStatus.ACTIVE);

        SeatType saved = seatTypeRepository.save(seatType);
        log.info("Created seat type: id={}, code={}, name={}, capacity={}", saved.getId(), saved.getCode(), saved.getName(), saved.getCapacity());
        return seatTypeMapper.toSeatTypeResponse(saved);
    }

    @Override
    @Transactional
    public SeatTypeResponse updateSeatType(String id, UpdateSeatTypeRequest request) {
        SeatType seatType = seatTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SeatType not found with id: " + id));

        String code = request.getCode().trim().toUpperCase();
        String name = request.getName().trim();

        if (seatTypeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Loại ghế với mã '" + code + "' đã tồn tại");
        }

        if (seatTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Loại ghế với tên '" + name + "' đã tồn tại");
        }

        validatePresentationMetadata(request.getColorToken(), request.getIcon());

        // Capacity Safety Invariant: immutable if referenced by existing seats
        if (request.getCapacity() != null && !request.getCapacity().equals(seatType.getCapacity())) {
            if (seatRepository.existsBySeatTypeId(id)) {
                throw new ConflictException("Không thể thay đổi sức chứa (capacity) của loại ghế '" + seatType.getName()
                        + "' vì đã có ghế trong phòng chiếu đang áp dụng loại ghế này.");
            }
            seatType.setCapacity(request.getCapacity());
        }

        seatType.setCode(code);
        seatType.setName(name);
        seatType.setPriceModifier(request.getPriceModifier());
        seatType.setColorToken(StringUtils.hasText(request.getColorToken()) ? request.getColorToken().trim().toLowerCase() : "slate");
        seatType.setIcon(StringUtils.hasText(request.getIcon()) ? request.getIcon().trim().toLowerCase() : "armchair");
        seatType.setDescription(request.getDescription());
        seatType.setStatus(request.getStatus());

        SeatType updated = seatTypeRepository.save(seatType);
        log.info("Updated seat type: id={}, code={}, name={}, capacity={}", updated.getId(), updated.getCode(), updated.getName(), updated.getCapacity());
        return seatTypeMapper.toSeatTypeResponse(updated);
    }

    @Override
    @Transactional
    public SeatType getOrCreateDefaultSeatType(String preferredId) {
        if (StringUtils.hasText(preferredId)) {
            return seatTypeRepository.findById(preferredId)
                    .orElseThrow(() -> new ResourceNotFoundException("SeatType not found with id: " + preferredId));
        }

        return seatTypeRepository.findByCodeIgnoreCase("STANDARD")
                .or(() -> seatTypeRepository.findByNameIgnoreCase("STANDARD"))
                .orElseGet(() -> {
                    SeatType defaultType = new SeatType();
                    defaultType.setCode("STANDARD");
                    defaultType.setName("Standard");
                    defaultType.setPriceModifier(BigDecimal.ZERO);
                    defaultType.setCapacity((short) 1);
                    defaultType.setColorToken("slate");
                    defaultType.setIcon("armchair");
                    defaultType.setDescription("Standard comfortable cinema seat");
                    defaultType.setStatus(SeatTypeStatus.ACTIVE);
                    return seatTypeRepository.save(defaultType);
                });
    }
}