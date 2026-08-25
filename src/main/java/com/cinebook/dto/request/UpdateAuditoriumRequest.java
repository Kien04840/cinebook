package com.cinebook.dto.request;

import com.cinebook.enums.AuditoriumStatus;
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
public class UpdateAuditoriumRequest {

    @NotBlank(message = "Auditorium name is required")
    @Size(max = 100, message = "Auditorium name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Auditorium type is required")
    @Size(max = 20, message = "Auditorium type cannot exceed 20 characters")
    private String type;

    @NotNull(message = "Status is required")
    private AuditoriumStatus status;
}