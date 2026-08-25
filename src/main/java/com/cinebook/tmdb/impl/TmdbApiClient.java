package com.cinebook.tmdb.impl;

import com.cinebook.config.TmdbProperties;
import com.cinebook.dto.tmdb.TmdbGenreListResponse;
import com.cinebook.dto.tmdb.TmdbMovieDetailDto;
import com.cinebook.exception.TmdbAuthException;
import com.cinebook.exception.TmdbResourceNotFoundException;
import com.cinebook.exception.TmdbServiceException;
import com.cinebook.tmdb.TmdbClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring RestClient-based implementation of TmdbClient.
 *
 * Uses append_to_response=credits,videos,release_dates to fetch all needed data
 * in a single network call for movie import, minimizing TMDB API rate-limit impact.
 */
@Slf4j
@Component
public class TmdbApiClient implements TmdbClient {

    private final TmdbProperties tmdbProperties;
    private final RestClient restClient;

    public TmdbApiClient(TmdbProperties tmdbProperties, RestClient.Builder restClientBuilder) {
        this.tmdbProperties = tmdbProperties;
        this.restClient = restClientBuilder
                .baseUrl(tmdbProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + tmdbProperties.getApiKey())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public TmdbGenreListResponse getMovieGenres(String language) {
        log.debug("Fetching TMDB genre list, language={}", language);
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/genre/movie/list")
                            .queryParam("language", language)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int status = resp.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            throw new TmdbAuthException("HTTP " + status);
                        }
                        throw new TmdbServiceException("TMDB genre list returned HTTP " + status);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new TmdbServiceException("TMDB returned HTTP " + resp.getStatusCode().value());
                    })
                    .body(TmdbGenreListResponse.class);
        } catch (TmdbAuthException | TmdbServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            // Network timeout or connection refused
            throw new TmdbServiceException("Network error calling TMDB genre list: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new TmdbServiceException("Unexpected error calling TMDB genre list: " + e.getMessage(), e);
        }
    }

    @Override
    public TmdbMovieDetailDto getMovieDetail(Long tmdbId, String language) {
        log.debug("Fetching TMDB movie detail, tmdbId={}, language={}", tmdbId, language);
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/movie/{id}")
                            .queryParam("language", language)
                            .queryParam("append_to_response", "credits,videos,release_dates")
                            .build(tmdbId))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int status = resp.getStatusCode().value();
                        if (status == 404) {
                            throw new TmdbResourceNotFoundException(tmdbId);
                        }
                        if (status == 401 || status == 403) {
                            throw new TmdbAuthException("HTTP " + status);
                        }
                        throw new TmdbServiceException("TMDB movie detail returned HTTP " + status);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new TmdbServiceException("TMDB returned HTTP " + resp.getStatusCode().value());
                    })
                    .body(TmdbMovieDetailDto.class);
        } catch (TmdbResourceNotFoundException | TmdbAuthException | TmdbServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new TmdbServiceException("Network error calling TMDB movie detail (tmdbId=" + tmdbId + "): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new TmdbServiceException("Unexpected error calling TMDB movie detail (tmdbId=" + tmdbId + "): " + e.getMessage(), e);
        }
    }
}
