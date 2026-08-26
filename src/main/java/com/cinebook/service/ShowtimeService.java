package com.cinebook.service;

import com.cinebook.dto.request.CreateShowtimeRequest;
import com.cinebook.dto.request.UpdateShowtimeRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.ShowtimeDetailResponse;
import com.cinebook.dto.response.ShowtimeSummaryResponse;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface ShowtimeService {

    PageResponse<ShowtimeSummaryResponse> getPublicShowtimes(
            String movieId,
            String cinemaId,
            String auditoriumId,
            LocalDate date,
            ShowtimeFormat format,
            String language,
            Pageable pageable
    );

    ShowtimeDetailResponse getPublicShowtimeDetail(String id);

    PageResponse<ShowtimeSummaryResponse> getAdminShowtimes(
            String movieId,
            String cinemaId,
            String auditoriumId,
            LocalDate date,
            ShowtimeStatus status,
            ShowtimeFormat format,
            String language,
            Pageable pageable
    );

    ShowtimeDetailResponse getAdminShowtimeDetail(String id);

    ShowtimeDetailResponse createShowtime(CreateShowtimeRequest request);

    ShowtimeDetailResponse updateShowtime(String id, UpdateShowtimeRequest request);

    void deleteShowtime(String id);
}