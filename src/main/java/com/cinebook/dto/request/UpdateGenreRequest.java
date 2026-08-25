package com.cinebook.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class UpdateGenreRequest {

    @NotBlank(message = "Genre name is required")
    @Size(max = 100, message = "Genre name cannot exceed 100 characters")
    private String name;

    @Size(max = 255, message = "Genre description cannot exceed 255 characters")
    private String description;
}

