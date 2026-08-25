package com.cinebook.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TmdbGenreDto {

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("name")
    private String name;
}