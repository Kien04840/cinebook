package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizeEmptyLayoutsResponse {

    private int processedCount; // Same as scannedCount
    private int scannedCount;
    private int updatedCount;   // Same as normalizedCount
    private int normalizedCount;
    private int unchangedCount;
    private int skippedCount;
    private int skippedBecauseBookings;
    private int skippedBecauseTickets;
    private int skippedBecauseActiveHolds;
    private int failedCount;
    private List<String> updatedAuditoriumIds;
    private List<SkippedItem> skippedDetails;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkippedItem {
        private String auditoriumId;
        private String auditoriumName;
        private String cinemaName;
        private String reason;
    }
}

