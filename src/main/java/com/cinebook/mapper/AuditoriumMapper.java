package com.cinebook.mapper;

import com.cinebook.dto.response.AuditoriumDetailResponse;
import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.SeatResponse;
import com.cinebook.entity.Auditorium;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuditoriumMapper {

    private final SeatMapper seatMapper;

    public AuditoriumResponse toAuditoriumResponse(Auditorium auditorium) {
        if (auditorium == null) {
            return null;
        }

        int totalSeats = 0;
        if (auditorium.getSeats() != null && !auditorium.getSeats().isEmpty()) {
            totalSeats = auditorium.getSeats().size();
        } else if (auditorium.getRowsCount() != null && auditorium.getColumnsCount() != null) {
            totalSeats = auditorium.getRowsCount() * auditorium.getColumnsCount();
        }

        String cinemaId = auditorium.getCinema() != null ? auditorium.getCinema().getId() : null;
        String cinemaName = auditorium.getCinema() != null ? auditorium.getCinema().getName() : null;

        return AuditoriumResponse.builder()
                .id(auditorium.getId())
                .cinemaId(cinemaId)
                .cinemaName(cinemaName)
                .name(auditorium.getName())
                .type(auditorium.getType())
                .rowsCount(auditorium.getRowsCount())
                .columnsCount(auditorium.getColumnsCount())
                .totalSeats(totalSeats)
                .status(auditorium.getStatus())
                .turnaroundMinutes(auditorium.getTurnaroundMinutes())
                .snapIntervalMinutes(auditorium.getSnapIntervalMinutes())
                .createdAt(auditorium.getCreatedAt())
                .updatedAt(auditorium.getUpdatedAt())
                .build();
    }

    public AuditoriumDetailResponse toAuditoriumDetailResponse(Auditorium auditorium) {
        if (auditorium == null) {
            return null;
        }

        List<SeatResponse> seatResponses = Collections.emptyList();
        if (auditorium.getSeats() != null && !auditorium.getSeats().isEmpty()) {
            seatResponses = auditorium.getSeats().stream()
                    .sorted(Comparator.comparing(com.cinebook.entity.Seat::getRowLabel)
                            .thenComparing(com.cinebook.entity.Seat::getSeatNumber))
                    .map(seatMapper::toSeatResponse)
                    .toList();
        }

        int totalSeats = seatResponses.isEmpty() && auditorium.getRowsCount() != null && auditorium.getColumnsCount() != null
                ? auditorium.getRowsCount() * auditorium.getColumnsCount()
                : seatResponses.size();

        String cinemaId = auditorium.getCinema() != null ? auditorium.getCinema().getId() : null;
        String cinemaName = auditorium.getCinema() != null ? auditorium.getCinema().getName() : null;

        return AuditoriumDetailResponse.builder()
                .id(auditorium.getId())
                .cinemaId(cinemaId)
                .cinemaName(cinemaName)
                .name(auditorium.getName())
                .type(auditorium.getType())
                .rowsCount(auditorium.getRowsCount())
                .columnsCount(auditorium.getColumnsCount())
                .totalSeats(totalSeats)
                .status(auditorium.getStatus())
                .turnaroundMinutes(auditorium.getTurnaroundMinutes())
                .snapIntervalMinutes(auditorium.getSnapIntervalMinutes())
                .seats(seatResponses)
                .createdAt(auditorium.getCreatedAt())
                .updatedAt(auditorium.getUpdatedAt())
                .build();
    }
}