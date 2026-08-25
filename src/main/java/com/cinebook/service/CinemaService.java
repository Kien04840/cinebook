package com.cinebook.service;

import com.cinebook.dto.request.CreateCinemaRequest;
import com.cinebook.dto.request.UpdateCinemaRequest;
import com.cinebook.dto.response.CinemaDetailResponse;
import com.cinebook.dto.response.CinemaSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.CinemaStatus;
import org.springframework.data.domain.Pageable;

public interface CinemaService {

    PageResponse<CinemaSummaryResponse> getPublicCinemas(
            String city,
            CinemaStatus status,
            String q,
            Pageable pageable
    );

    CinemaDetailResponse getPublicCinemaDetail(String id);

    PageResponse<CinemaSummaryResponse> getAdminCinemas(
            String city,
            CinemaStatus status,
            String q,
            Boolean includeDeleted,
            Pageable pageable
    );

    CinemaDetailResponse getAdminCinemaDetail(String id);

    CinemaDetailResponse createCinema(CreateCinemaRequest request);

    CinemaDetailResponse updateCinema(String id, UpdateCinemaRequest request);

    void deleteCinema(String id);
}