package com.cinebook.mapper;

import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.CinemaDetailResponse;
import com.cinebook.dto.response.CinemaSummaryResponse;
import com.cinebook.entity.Cinema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CinemaMapper {

    private final AuditoriumMapper auditoriumMapper;

    public CinemaSummaryResponse toCinemaSummaryResponse(Cinema cinema) {
        if (cinema == null) {
            return null;
        }

        int auditoriumsCount = 0;
        if (cinema.getAuditoriums() != null) {
            auditoriumsCount = (int) cinema.getAuditoriums().stream()
                    .filter(a -> a.getDeletedAt() == null)
                    .count();
        }

        return CinemaSummaryResponse.builder()
                .id(cinema.getId())
                .name(cinema.getName())
                .address(cinema.getAddress())
                .city(cinema.getCity())
                .status(cinema.getStatus())
                .auditoriumsCount(auditoriumsCount)
                .createdAt(cinema.getCreatedAt())
                .updatedAt(cinema.getUpdatedAt())
                .build();
    }

    public CinemaDetailResponse toCinemaDetailResponse(Cinema cinema) {
        if (cinema == null) {
            return null;
        }

        List<AuditoriumResponse> auditoriumResponses = Collections.emptyList();
        if (cinema.getAuditoriums() != null) {
            auditoriumResponses = cinema.getAuditoriums().stream()
                    .filter(a -> a.getDeletedAt() == null)
                    .map(auditoriumMapper::toAuditoriumResponse)
                    .toList();
        }

        return CinemaDetailResponse.builder()
                .id(cinema.getId())
                .name(cinema.getName())
                .address(cinema.getAddress())
                .city(cinema.getCity())
                .status(cinema.getStatus())
                .auditoriums(auditoriumResponses)
                .createdAt(cinema.getCreatedAt())
                .updatedAt(cinema.getUpdatedAt())
                .build();
    }
}