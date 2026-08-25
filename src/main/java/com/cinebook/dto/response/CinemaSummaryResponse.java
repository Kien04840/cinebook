package com.cinebook.dto.response;

import com.cinebook.enums.CinemaStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaSummaryResponse {

    private String id;
    private String name;
    private String address;
    private String city;
    private CinemaStatus status;
    private int auditoriumsCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}