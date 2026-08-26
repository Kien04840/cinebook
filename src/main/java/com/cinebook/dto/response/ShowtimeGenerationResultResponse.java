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
public class ShowtimeGenerationResultResponse {
    private int totalCreated;
    private int totalSkipped;
    private int totalConflicted;
    @Builder.Default
    private List<ShowtimeSummaryResponse> createdShowtimes = new ArrayList<>();
    @Builder.Default
    private List<SchedulingConflictResponse> conflicts = new ArrayList<>();
}