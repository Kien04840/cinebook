package com.cinebook.service.scheduling;

import com.cinebook.dto.response.SchedulingConflictResponse;
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
public class SchedulingValidationResult {
    private boolean valid;
    @Builder.Default
    private List<SchedulingConflictResponse> conflicts = new ArrayList<>();

    public static SchedulingValidationResult success() {
        return SchedulingValidationResult.builder()
                .valid(true)
                .conflicts(new ArrayList<>())
                .build();
    }

    public static SchedulingValidationResult failed(List<SchedulingConflictResponse> conflicts) {
        return SchedulingValidationResult.builder()
                .valid(false)
                .conflicts(conflicts != null ? conflicts : new ArrayList<>())
                .build();
    }

    public static SchedulingValidationResult failed(SchedulingConflictResponse conflict) {
        List<SchedulingConflictResponse> list = new ArrayList<>();
        if (conflict != null) {
            list.add(conflict);
        }
        return failed(list);
    }
}