package com.cinebook.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TmdbGenreListResponse {

    @JsonProperty("genres")
    private List<TmdbGenreDto> genres;
}
