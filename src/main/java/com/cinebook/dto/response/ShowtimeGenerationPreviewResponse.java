package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeGenerationPreviewResponse {
    private int totalProposed;
    private int totalValid;
    private int totalConflicted;
    private int totalRequested;
    private int totalScheduled;
    private int totalUnscheduled;

    @Builder.Default
    private List<MovieGenerationSummaryDto> movieSummaries = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<String> qualityIndicators = new ArrayList<>();

    @Builder.Default
    private List<ShowtimeSlotPreviewResponse> slots = new ArrayList<>();
}