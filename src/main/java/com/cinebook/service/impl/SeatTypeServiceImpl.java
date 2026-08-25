package com.cinebook.service.impl;

import com.cinebook.dto.request.CreateSeatTypeRequest;
import com.cinebook.dto.request.UpdateSeatTypeRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.SeatTypeResponse;
import com.cinebook.entity.SeatType;
import com.cinebook.enums.SeatTypeStatus;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.SeatTypeMapper;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatTypeServiceImpl implements SeatTypeService {

    private final SeatTypeRepository seatTypeRepository;
    private final SeatTypeMapper seatTypeMapper;

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
        if (seatTypeRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new ConflictException("SeatType with name '" + request.getName() + "' already exists");
        }

        SeatType seatType = new SeatType();
        seatType.setName(request.getName().trim());
        seatType.setPriceModifier(request.getPriceModifier());
        seatType.setDescription(request.getDescription());
        seatType.setStatus(request.getStatus() != null ? request.getStatus() : SeatTypeStatus.ACTIVE);

        SeatType saved = seatTypeRepository.save(seatType);
        log.info("Created seat type: id={}, name={}", saved.getId(), saved.getName());
        return seatTypeMapper.toSeatTypeResponse(saved);
    }

    @Override
    @Transactional
    public SeatTypeResponse updateSeatType(String id, UpdateSeatTypeRequest request) {
        SeatType seatType = seatTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SeatType not found with id: " + id));

        if (seatTypeRepository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new ConflictException("SeatType with name '" + request.getName() + "' already exists");
        }

        seatType.setName(request.getName().trim());
        seatType.setPriceModifier(request.getPriceModifier());
        seatType.setDescription(request.getDescription());
        seatType.setStatus(request.getStatus());

        SeatType updated = seatTypeRepository.save(seatType);
        log.info("Updated seat type: id={}, name={}", updated.getId(), updated.getName());
        return seatTypeMapper.toSeatTypeResponse(updated);
    }

    @Override
    @Transactional
    public SeatType getOrCreateDefaultSeatType(String preferredId) {
        if (StringUtils.hasText(preferredId)) {
            return seatTypeRepository.findById(preferredId)
                    .orElseThrow(() -> new ResourceNotFoundException("SeatType not found with id: " + preferredId));
        }

        return seatTypeRepository.findByNameIgnoreCase("STANDARD")
                .orElseGet(() -> {
                    SeatType defaultType = new SeatType();
                    defaultType.setName("STANDARD");
                    defaultType.setPriceModifier(BigDecimal.ZERO);
                    defaultType.setDescription("Standard comfortable cinema seat");
                    defaultType.setStatus(SeatTypeStatus.ACTIVE);
                    return seatTypeRepository.save(defaultType);
                });
    }
}