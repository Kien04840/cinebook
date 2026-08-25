package com.cinebook.service.impl;

import com.cinebook.dto.request.CreateCinemaRequest;
import com.cinebook.dto.request.UpdateCinemaRequest;
import com.cinebook.dto.response.CinemaDetailResponse;
import com.cinebook.dto.response.CinemaSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.entity.Cinema;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.CinemaMapper;
import com.cinebook.repository.CinemaRepository;
import com.cinebook.repository.specification.CinemaSpecification;
import com.cinebook.service.CinemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CinemaServiceImpl implements CinemaService {

    private final CinemaRepository cinemaRepository;
    private final CinemaMapper cinemaMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CinemaSummaryResponse> getPublicCinemas(
            String city,
            CinemaStatus status,
            String q,
            Pageable pageable
    ) {
        Specification<Cinema> spec = Specification.where(CinemaSpecification.isNotDeleted())
                .and(CinemaSpecification.hasCity(city))
                .and(CinemaSpecification.hasStatus(status != null ? status : CinemaStatus.ACTIVE))
                .and(CinemaSpecification.searchKeyword(q));

        Page<Cinema> page = cinemaRepository.findAll(spec, pageable);
        return PageResponse.of(page, cinemaMapper::toCinemaSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CinemaDetailResponse getPublicCinemaDetail(String id) {
        Cinema cinema = cinemaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found with id: " + id));

        return cinemaMapper.toCinemaDetailResponse(cinema);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CinemaSummaryResponse> getAdminCinemas(
            String city,
            CinemaStatus status,
            String q,
            Boolean includeDeleted,
            Pageable pageable
    ) {
        Specification<Cinema> spec = (root, query, cb) -> cb.conjunction();

        if (includeDeleted == null || !includeDeleted) {
            spec = spec.and(CinemaSpecification.isNotDeleted());
        }

        spec = spec.and(CinemaSpecification.hasCity(city))
                .and(CinemaSpecification.hasStatus(status))
                .and(CinemaSpecification.searchKeyword(q));

        Page<Cinema> page = cinemaRepository.findAll(spec, pageable);
        return PageResponse.of(page, cinemaMapper::toCinemaSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CinemaDetailResponse getAdminCinemaDetail(String id) {
        Cinema cinema = cinemaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found with id: " + id));

        return cinemaMapper.toCinemaDetailResponse(cinema);
    }

    @Override
    @Transactional
    public CinemaDetailResponse createCinema(CreateCinemaRequest request) {
        if (cinemaRepository.existsByNameAndCityAndDeletedAtIsNull(request.getName().trim(), request.getCity().trim())) {
            throw new ConflictException("Cinema with name '" + request.getName() + "' in city '" + request.getCity() + "' already exists");
        }

        Cinema cinema = new Cinema();
        cinema.setName(request.getName().trim());
        cinema.setAddress(request.getAddress().trim());
        cinema.setCity(request.getCity().trim());
        cinema.setStatus(request.getStatus() != null ? request.getStatus() : CinemaStatus.ACTIVE);

        Cinema saved = cinemaRepository.save(cinema);
        log.info("Created cinema: id={}, name={}, city={}", saved.getId(), saved.getName(), saved.getCity());
        return cinemaMapper.toCinemaDetailResponse(saved);
    }

    @Override
    @Transactional
    public CinemaDetailResponse updateCinema(String id, UpdateCinemaRequest request) {
        Cinema cinema = cinemaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found with id: " + id));

        cinema.setName(request.getName().trim());
        cinema.setAddress(request.getAddress().trim());
        cinema.setCity(request.getCity().trim());
        cinema.setStatus(request.getStatus());

        Cinema updated = cinemaRepository.save(cinema);
        log.info("Updated cinema: id={}, name={}", updated.getId(), updated.getName());
        return cinemaMapper.toCinemaDetailResponse(updated);
    }

    @Override
    @Transactional
    public void deleteCinema(String id) {
        Cinema cinema = cinemaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found with id: " + id));

        cinema.setDeletedAt(LocalDateTime.now());
        cinema.setStatus(CinemaStatus.CLOSED);
        cinemaRepository.save(cinema);
        log.info("Soft-deleted cinema: id={}", id);
    }
}