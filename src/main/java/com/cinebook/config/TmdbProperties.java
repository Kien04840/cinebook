package com.cinebook.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "tmdb")
public class TmdbProperties {

    private String apiKey;
    private String baseUrl = "https://api.themoviedb.org/3";
    private String imageBaseUrl = "https://image.tmdb.org/t/p";
    private String language = "en-US";
    private String posterSize = "w500";
    private String backdropSize = "original";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;

    /**
     * Builds a full poster URL from a TMDB poster_path.
     * Returns null if path is null or blank.
     */
    public String buildPosterUrl(String posterPath) {
        if (posterPath == null || posterPath.isBlank()) {
            return null;
        }
        String path = posterPath.startsWith("/") ? posterPath : "/" + posterPath;
        return imageBaseUrl + "/" + posterSize + path;
    }

    /**
     * Builds a full backdrop URL from a TMDB backdrop_path.
     * Returns null if path is null or blank.
     */
    public String buildBackdropUrl(String backdropPath) {
        if (backdropPath == null || backdropPath.isBlank()) {
            return null;
        }
        String path = backdropPath.startsWith("/") ? backdropPath : "/" + backdropPath;
        return imageBaseUrl + "/" + backdropSize + path;
    }
}
