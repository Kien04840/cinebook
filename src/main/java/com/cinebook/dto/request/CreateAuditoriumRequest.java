package com.cinebook.dto.request;

import com.cinebook.enums.AuditoriumStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateAuditoriumRequest {

    @NotBlank(message = "Auditorium name is required")
    @Size(max = 100, message = "Auditorium name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Auditorium type is required")
    @Size(max = 20, message = "Auditorium type cannot exceed 20 characters")
    private String type;

    @NotNull(message = "Rows count is required")
    @Min(value = 1, message = "Rows count must be at least 1")
    @Max(value = 26, message = "Rows count cannot exceed 26 (A-Z)")
    private Short rowsCount;

    @NotNull(message = "Columns count is required")
    @Min(value = 1, message = "Columns count must be at least 1")
    @Max(value = 50, message = "Columns count cannot exceed 50")
    private Short columnsCount;

    private AuditoriumStatus status;

    @Min(value = 0, message = "Turnaround minutes cannot be negative")
    private Short turnaroundMinutes;

    @Min(value = 1, message = "Snap interval minutes must be at least 1")
    private Short snapIntervalMinutes;

    private String defaultSeatTypeId;
}