package com.cinebook.dto.response;

import com.cinebook.enums.AuditoriumStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriumSchedulingConfigResponse {
    private String id;
    private String name;
    private String type;
    private AuditoriumStatus status;
    private Short turnaroundMinutes;
    private Short snapIntervalMinutes;
}