package com.cinebook.service;

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
import com.cinebook.service.impl.SeatTypeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatTypeServiceTest {

    @Mock
    private SeatTypeRepository seatTypeRepository;

    @Spy
    private SeatTypeMapper seatTypeMapper = new SeatTypeMapper();

    @InjectMocks
    private SeatTypeServiceImpl seatTypeService;

    private SeatType sampleSeatType;

    @BeforeEach
    void setUp() {
        sampleSeatType = new SeatType();
        sampleSeatType.setId("st-1");
        sampleSeatType.setName("STANDARD");
        sampleSeatType.setPriceModifier(BigDecimal.ZERO);
        sampleSeatType.setStatus(SeatTypeStatus.ACTIVE);
    }

    @Test
    void getAllActiveSeatTypes_Success() {
        when(seatTypeRepository.findByStatus(SeatTypeStatus.ACTIVE)).thenReturn(List.of(sampleSeatType));

        List<SeatTypeResponse> result = seatTypeService.getAllActiveSeatTypes();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("STANDARD", result.get(0).getName());
    }

    @Test
    void getAdminSeatTypes_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<SeatType> page = new PageImpl<>(List.of(sampleSeatType), pageable, 1);
        when(seatTypeRepository.findAll(pageable)).thenReturn(page);

        PageResponse<SeatTypeResponse> result = seatTypeService.getAdminSeatTypes(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void createSeatType_Success() {
        CreateSeatTypeRequest request = CreateSeatTypeRequest.builder()
                .name("VIP")
                .priceModifier(new BigDecimal("30000.00"))
                .description("VIP seat")
                .status(SeatTypeStatus.ACTIVE)
                .build();

        when(seatTypeRepository.existsByNameIgnoreCase("VIP")).thenReturn(false);
        when(seatTypeRepository.save(any(SeatType.class))).thenAnswer(inv -> {
            SeatType st = inv.getArgument(0);
            st.setId("st-vip");
            return st;
        });

        SeatTypeResponse result = seatTypeService.createSeatType(request);

        assertNotNull(result);
        assertEquals("VIP", result.getName());
        assertEquals(new BigDecimal("30000.00"), result.getPriceModifier());
    }

    @Test
    void createSeatType_DuplicateName_ThrowsConflict() {
        CreateSeatTypeRequest request = CreateSeatTypeRequest.builder()
                .name("STANDARD")
                .priceModifier(BigDecimal.ZERO)
                .build();

        when(seatTypeRepository.existsByNameIgnoreCase("STANDARD")).thenReturn(true);

        assertThrows(ConflictException.class, () -> seatTypeService.createSeatType(request));
    }

    @Test
    void updateSeatType_Success() {
        UpdateSeatTypeRequest request = UpdateSeatTypeRequest.builder()
                .name("STANDARD_PLUS")
                .priceModifier(new BigDecimal("10000.00"))
                .status(SeatTypeStatus.ACTIVE)
                .build();

        when(seatTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleSeatType));
        when(seatTypeRepository.existsByNameIgnoreCaseAndIdNot("STANDARD_PLUS", "st-1")).thenReturn(false);
        when(seatTypeRepository.save(any(SeatType.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatTypeResponse result = seatTypeService.updateSeatType("st-1", request);

        assertNotNull(result);
        assertEquals("STANDARD_PLUS", result.getName());
    }

    @Test
    void getOrCreateDefaultSeatType_PreferredFound() {
        when(seatTypeRepository.findById("st-1")).thenReturn(Optional.of(sampleSeatType));

        SeatType result = seatTypeService.getOrCreateDefaultSeatType("st-1");

        assertNotNull(result);
        assertEquals("st-1", result.getId());
    }

    @Test
    void getOrCreateDefaultSeatType_StandardFallback() {
        when(seatTypeRepository.findByNameIgnoreCase("STANDARD")).thenReturn(Optional.of(sampleSeatType));

        SeatType result = seatTypeService.getOrCreateDefaultSeatType(null);

        assertNotNull(result);
        assertEquals("STANDARD", result.getName());
    }
}