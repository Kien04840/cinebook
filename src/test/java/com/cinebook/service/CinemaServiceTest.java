package com.cinebook.service;

import com.cinebook.dto.request.CreateCinemaRequest;
import com.cinebook.dto.request.UpdateCinemaRequest;
import com.cinebook.dto.response.CinemaDetailResponse;
import com.cinebook.dto.response.CinemaSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.entity.Cinema;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.AuditoriumMapper;
import com.cinebook.mapper.CinemaMapper;
import com.cinebook.mapper.SeatMapper;
import com.cinebook.repository.CinemaRepository;
import com.cinebook.service.impl.CinemaServiceImpl;
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
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CinemaServiceTest {

    @Mock
    private CinemaRepository cinemaRepository;

    @Spy
    private CinemaMapper cinemaMapper = new CinemaMapper(new AuditoriumMapper(new SeatMapper()));

    @InjectMocks
    private CinemaServiceImpl cinemaService;

    private Cinema sampleCinema;

    @BeforeEach
    void setUp() {
        sampleCinema = new Cinema();
        sampleCinema.setId("cin-1");
        sampleCinema.setName("CineBook Hanoi Center");
        sampleCinema.setAddress("123 Trang Tien, Hoan Kiem");
        sampleCinema.setCity("Hanoi");
        sampleCinema.setStatus(CinemaStatus.ACTIVE);
        sampleCinema.setAuditoriums(new HashSet<>());
        sampleCinema.setCreatedAt(LocalDateTime.now());
        sampleCinema.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void getPublicCinemas_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Cinema> page = new PageImpl<>(List.of(sampleCinema), pageable, 1);

        when(cinemaRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<CinemaSummaryResponse> result = cinemaService.getPublicCinemas("Hanoi", CinemaStatus.ACTIVE, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("CineBook Hanoi Center", result.getContent().get(0).getName());
    }

    @Test
    void getPublicCinemaDetail_Success() {
        when(cinemaRepository.findByIdAndDeletedAtIsNull("cin-1")).thenReturn(Optional.of(sampleCinema));

        CinemaDetailResponse result = cinemaService.getPublicCinemaDetail("cin-1");

        assertNotNull(result);
        assertEquals("cin-1", result.getId());
        assertEquals("CineBook Hanoi Center", result.getName());
    }

    @Test
    void getPublicCinemaDetail_NotFound_ThrowsException() {
        when(cinemaRepository.findByIdAndDeletedAtIsNull("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cinemaService.getPublicCinemaDetail("unknown"));
    }

    @Test
    void createCinema_Success() {
        CreateCinemaRequest request = CreateCinemaRequest.builder()
                .name("CineBook Saigon Mall")
                .address("456 Le Loi, District 1")
                .city("Ho Chi Minh City")
                .status(CinemaStatus.ACTIVE)
                .build();

        when(cinemaRepository.existsByNameAndCityAndDeletedAtIsNull("CineBook Saigon Mall", "Ho Chi Minh City")).thenReturn(false);
        when(cinemaRepository.save(any(Cinema.class))).thenAnswer(inv -> {
            Cinema c = inv.getArgument(0);
            c.setId("cin-new");
            return c;
        });

        CinemaDetailResponse result = cinemaService.createCinema(request);

        assertNotNull(result);
        assertEquals("CineBook Saigon Mall", result.getName());
        assertEquals("Ho Chi Minh City", result.getCity());
        verify(cinemaRepository).save(any(Cinema.class));
    }

    @Test
    void createCinema_DuplicateNameAndCity_ThrowsConflict() {
        CreateCinemaRequest request = CreateCinemaRequest.builder()
                .name("CineBook Hanoi Center")
                .address("123 Trang Tien")
                .city("Hanoi")
                .build();

        when(cinemaRepository.existsByNameAndCityAndDeletedAtIsNull("CineBook Hanoi Center", "Hanoi")).thenReturn(true);

        assertThrows(ConflictException.class, () -> cinemaService.createCinema(request));
        verify(cinemaRepository, never()).save(any(Cinema.class));
    }

    @Test
    void updateCinema_Success() {
        UpdateCinemaRequest request = UpdateCinemaRequest.builder()
                .name("CineBook Hanoi Premium")
                .address("123 Trang Tien updated")
                .city("Hanoi")
                .status(CinemaStatus.ACTIVE)
                .build();

        when(cinemaRepository.findByIdAndDeletedAtIsNull("cin-1")).thenReturn(Optional.of(sampleCinema));
        when(cinemaRepository.save(any(Cinema.class))).thenAnswer(inv -> inv.getArgument(0));

        CinemaDetailResponse result = cinemaService.updateCinema("cin-1", request);

        assertNotNull(result);
        assertEquals("CineBook Hanoi Premium", result.getName());
        assertEquals("123 Trang Tien updated", result.getAddress());
    }

    @Test
    void deleteCinema_Success() {
        when(cinemaRepository.findByIdAndDeletedAtIsNull("cin-1")).thenReturn(Optional.of(sampleCinema));

        cinemaService.deleteCinema("cin-1");

        assertNotNull(sampleCinema.getDeletedAt());
        assertEquals(CinemaStatus.CLOSED, sampleCinema.getStatus());
        verify(cinemaRepository).save(sampleCinema);
    }
}