package com.cinebook.service.scheduling;

import com.cinebook.dto.response.MovieGenerationSummaryDto;
import com.cinebook.dto.response.SchedulingConflictResponse;
import com.cinebook.dto.response.ShowtimeSlotPreviewResponse;
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
public class GenerationPlanningResult {

    @Builder.Default
    private List<ShowtimeSlotPreviewResponse> candidateSlots = new ArrayList<>();

    @Builder.Default
    private List<MovieGenerationSummaryDto> movieSummaries = new ArrayList<>();

    private int totalRequested;
    private int totalScheduled;
    private int totalUnscheduled;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<SchedulingConflictResponse> conflicts = new ArrayList<>();

    @Builder.Default
    private List<String> qualityIndicators = new ArrayList<>();
}

