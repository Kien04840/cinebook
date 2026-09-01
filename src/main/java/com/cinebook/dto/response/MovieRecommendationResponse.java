package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieRecommendationResponse {

    private String explanation;
    private List<String> favoriteGenres;
    private List<MovieSummaryResponse> movies;
}

