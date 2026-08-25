package com.cinebook.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Represents a TMDB /movie/{id} response with append_to_response=credits,videos,release_dates.
 * Only maps fields that CineBook needs; unknown fields are ignored.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbMovieDetailDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("original_title")
    private String originalTitle;

    @JsonProperty("overview")
    private String overview;

    @JsonProperty("runtime")
    private Integer runtime;

    @JsonProperty("release_date")
    private String releaseDate;

    @JsonProperty("original_language")
    private String originalLanguage;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    @JsonProperty("genres")
    private List<TmdbGenreDto> genres;

    @JsonProperty("production_countries")
    private List<TmdbProductionCountryDto> productionCountries;

    @JsonProperty("credits")
    private TmdbCreditsDto credits;

    @JsonProperty("videos")
    private TmdbVideosResultDto videos;

    /**
     * Release dates contain per-country certification info (age rating).
     * Populated when append_to_response=release_dates is used.
     */
    @JsonProperty("release_dates")
    private TmdbReleaseDatesDto releaseDates;

    // ---- Nested DTOs ----

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbProductionCountryDto {
        @JsonProperty("iso_3166_1")
        private String iso;

        @JsonProperty("name")
        private String name;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbCreditsDto {
        @JsonProperty("cast")
        private List<TmdbCastDto> cast;

        @JsonProperty("crew")
        private List<TmdbCrewDto> crew;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbCastDto {
        @JsonProperty("name")
        private String name;

        @JsonProperty("order")
        private Integer order;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbCrewDto {
        @JsonProperty("name")
        private String name;

        @JsonProperty("job")
        private String job;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbVideosResultDto {
        @JsonProperty("results")
        private List<TmdbVideoDto> results;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbVideoDto {
        @JsonProperty("key")
        private String key;

        @JsonProperty("site")
        private String site;

        @JsonProperty("type")
        private String type;

        @JsonProperty("official")
        private Boolean official;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbReleaseDatesDto {
        @JsonProperty("results")
        private List<TmdbReleaseDatesByCountryDto> results;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbReleaseDatesByCountryDto {
        @JsonProperty("iso_3166_1")
        private String country;

        @JsonProperty("release_dates")
        private List<TmdbReleaseDateEntryDto> releaseDates;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbReleaseDateEntryDto {
        /** US certification: "G", "PG", "PG-13", "R", "NC-17" etc. */
        @JsonProperty("certification")
        private String certification;

        @JsonProperty("type")
        private Integer type;
    }
}
