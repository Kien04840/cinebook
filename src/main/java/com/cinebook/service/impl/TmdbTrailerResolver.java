package com.cinebook.service.impl;

import com.cinebook.dto.tmdb.TmdbMovieDetailDto;
import com.cinebook.entity.Movie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves YouTube trailer URLs according to CineBook's multi-tier fallback strategy:
 *
 * Priority 1: Vietnamese Trailer (language = vi, type = Trailer, site = YouTube)
 * Priority 2: English Trailer (language = en, type = Trailer, site = YouTube)
 * Priority 3: Original Language Trailer (language = original_language, type = Trailer, site = YouTube)
 * Priority 4: Other valid YouTube Trailer (official = true, type = Trailer, site = YouTube, then general)
 * Fallback:   Valid YouTube Teaser (ONLY if movie has no existing trailer and no trailer exists on TMDB)
 *
 * Invariant: Never overwrite an existing trailer with null/empty or a lower-priority candidate.
 */
@Slf4j
public final class TmdbTrailerResolver {

    private static final String YOUTUBE_SITE = "YouTube";
    private static final String TYPE_TRAILER = "Trailer";
    private static final String TYPE_TEASER = "Teaser";
    private static final String OFFICIAL_TRAILER_KEYWORD = "official trailer";
    private static final String YOUTUBE_WATCH_PREFIX = "https://www.youtube.com/watch?v=";

    private TmdbTrailerResolver() {
        // Utility class
    }

    /**
     * Resolves the best trailer URL for a movie from TMDB videos.
     *
     * @param existingMovie    the current movie entity in database, or null if creating a new movie
     * @param originalLanguage the movie's original language (e.g., "en", "ja", "ko", "vi")
     * @param videos           the candidate videos from TMDB
     * @return the resolved YouTube trailer URL, or the existing trailer URL if preserved, or null
     */
    public static String resolveTrailerUrl(
            Movie existingMovie,
            String originalLanguage,
            List<TmdbMovieDetailDto.TmdbVideoDto> videos
    ) {
        String existingTrailerUrl = (existingMovie != null && StringUtils.hasText(existingMovie.getTrailerUrl()))
                ? existingMovie.getTrailerUrl().trim()
                : null;
        boolean hasExistingTrailer = StringUtils.hasText(existingTrailerUrl);

        if (videos == null || videos.isEmpty()) {
            if (hasExistingTrailer) {
                log.info("No TMDB videos available. Preserving existing trailer: {}", existingTrailerUrl);
                return existingTrailerUrl;
            }
            return null;
        }

        // Filter valid candidates: YouTube only, key present, type = Trailer or Teaser
        List<TmdbMovieDetailDto.TmdbVideoDto> validCandidates = videos.stream()
                .filter(Objects::nonNull)
                .filter(v -> StringUtils.hasText(v.getKey()))
                .filter(v -> YOUTUBE_SITE.equalsIgnoreCase(v.getSite()))
                .filter(v -> TYPE_TRAILER.equalsIgnoreCase(v.getType()) || TYPE_TEASER.equalsIgnoreCase(v.getType()))
                .toList();

        if (validCandidates.isEmpty()) {
            if (hasExistingTrailer) {
                log.info("No valid YouTube trailer/teaser found. Preserving existing trailer: {}", existingTrailerUrl);
                return existingTrailerUrl;
            }
            return null;
        }

        List<TmdbMovieDetailDto.TmdbVideoDto> trailerCandidates = validCandidates.stream()
                .filter(v -> TYPE_TRAILER.equalsIgnoreCase(v.getType()))
                .toList();

        // If NO trailers are available on TMDB (only teasers)
        if (trailerCandidates.isEmpty()) {
            if (hasExistingTrailer) {
                log.info("TMDB has only teasers/clips. Preserving existing trailer: {}", existingTrailerUrl);
                return existingTrailerUrl;
            }
            // If movie has no trailer at all, fallback to best valid YouTube teaser
            List<TmdbMovieDetailDto.TmdbVideoDto> teaserCandidates = validCandidates.stream()
                    .filter(v -> TYPE_TEASER.equalsIgnoreCase(v.getType()))
                    .toList();
            if (!teaserCandidates.isEmpty()) {
                TmdbMovieDetailDto.TmdbVideoDto bestTeaser = teaserCandidates.stream()
                        .min(getComparator(originalLanguage))
                        .orElse(null);
                if (bestTeaser != null) {
                    log.info("No trailer available. Falling back to teaser key={}", bestTeaser.getKey());
                    return buildYouTubeUrl(bestTeaser.getKey());
                }
            }
            return null;
        }

        // Pick best trailer candidate
        Comparator<TmdbMovieDetailDto.TmdbVideoDto> comparator = getComparator(originalLanguage);
        TmdbMovieDetailDto.TmdbVideoDto bestCandidate = trailerCandidates.stream()
                .min(comparator)
                .orElse(null);

        if (bestCandidate == null) {
            return existingTrailerUrl;
        }

        if (!hasExistingTrailer) {
            log.info("Selected trailer key={} (langTier={}, official={})",
                    bestCandidate.getKey(),
                    getLanguageTier(bestCandidate, originalLanguage),
                    bestCandidate.getOfficial());
            return buildYouTubeUrl(bestCandidate.getKey());
        }

        // Movie already has a trailer: evaluate whether to upgrade or preserve
        String existingKey = extractYouTubeKey(existingTrailerUrl);
        Optional<TmdbMovieDetailDto.TmdbVideoDto> existingVideoOpt = trailerCandidates.stream()
                .filter(v -> v.getKey() != null && v.getKey().equals(existingKey))
                .findFirst();

        if (existingVideoOpt.isPresent()) {
            TmdbMovieDetailDto.TmdbVideoDto existingVideo = existingVideoOpt.get();
            int comparison = comparator.compare(bestCandidate, existingVideo);
            if (comparison < 0) {
                log.info("Upgrading trailer from key={} to key={} (higher priority)",
                        existingVideo.getKey(), bestCandidate.getKey());
                return buildYouTubeUrl(bestCandidate.getKey());
            } else {
                log.info("Preserving existing trailer key={} (candidate key={} does not have higher priority)",
                        existingVideo.getKey(), bestCandidate.getKey());
                return existingTrailerUrl;
            }
        } else {
            // Existing key is not in TMDB list
            // Never downgrade an existing trailer to an unofficial candidate
            if (!Boolean.TRUE.equals(bestCandidate.getOfficial())) {
                log.info("Preserving existing trailer: new candidate is not official");
                return existingTrailerUrl;
            }

            // If bestCandidate is an official Vietnamese trailer (Priority 1), upgrade to it
            if (getLanguageTier(bestCandidate, originalLanguage) == 1) {
                log.info("Upgrading existing trailer to official Vietnamese trailer key={}", bestCandidate.getKey());
                return buildYouTubeUrl(bestCandidate.getKey());
            }

            // Otherwise, keep existing trailer safely
            log.info("Preserving existing trailer {}", existingTrailerUrl);
            return existingTrailerUrl;
        }
    }

    /**
     * Builds standard YouTube watch URL from key.
     */
    public static String buildYouTubeUrl(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return YOUTUBE_WATCH_PREFIX + key.trim();
    }

    /**
     * Extracts YouTube key from URL or raw key string.
     */
    public static String extractYouTubeKey(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.contains("v=")) {
            String afterV = trimmed.substring(trimmed.indexOf("v=") + 2);
            int ampIdx = afterV.indexOf('&');
            return ampIdx > 0 ? afterV.substring(0, ampIdx) : afterV;
        }
        if (trimmed.contains("youtu.be/")) {
            String afterSlash = trimmed.substring(trimmed.indexOf("youtu.be/") + 9);
            int qIdx = afterSlash.indexOf('?');
            return qIdx > 0 ? afterSlash.substring(0, qIdx) : afterSlash;
        }
        if (trimmed.contains("embed/")) {
            String afterEmbed = trimmed.substring(trimmed.indexOf("embed/") + 6);
            int qIdx = afterEmbed.indexOf('?');
            return qIdx > 0 ? afterEmbed.substring(0, qIdx) : afterEmbed;
        }
        if (!trimmed.contains("/") && !trimmed.contains(".")) {
            return trimmed;
        }
        return null;
    }

    /**
     * Returns comparator implementing the multi-tier trailer ranking:
     * 1. Language tier (1: vi, 2: en, 3: original language, 4: other)
     * 2. Official flag (true > false/null)
     * 3. Name contains "Official Trailer"
     * 4. Published date (newer > older)
     */
    public static Comparator<TmdbMovieDetailDto.TmdbVideoDto> getComparator(String originalLanguage) {
        return (a, b) -> {
            // 1. Language Tier (lower number = higher priority)
            int langCompare = Integer.compare(
                    getLanguageTier(a, originalLanguage),
                    getLanguageTier(b, originalLanguage)
            );
            if (langCompare != 0) {
                return langCompare;
            }

            // 2. Official flag (true preferred)
            boolean aOfficial = Boolean.TRUE.equals(a.getOfficial());
            boolean bOfficial = Boolean.TRUE.equals(b.getOfficial());
            if (aOfficial != bOfficial) {
                return aOfficial ? -1 : 1;
            }

            // 3. Name contains "Official Trailer"
            boolean aTitle = hasOfficialKeyword(a.getName());
            boolean bTitle = hasOfficialKeyword(b.getName());
            if (aTitle != bTitle) {
                return aTitle ? -1 : 1;
            }

            // 4. Published date (newer date preferred)
            Instant aDate = parsePublishedAt(a.getPublishedAt());
            Instant bDate = parsePublishedAt(b.getPublishedAt());
            int dateCompare = bDate.compareTo(aDate); // newer -> negative (earlier in sort order)
            if (dateCompare != 0) {
                return dateCompare;
            }

            return 0;
        };
    }

    /**
     * Computes the language priority tier:
     * Tier 1: Vietnamese
     * Tier 2: English
     * Tier 3: Original language of the movie
     * Tier 4: Other / unspecified
     */
    public static int getLanguageTier(TmdbMovieDetailDto.TmdbVideoDto video, String originalLanguage) {
        if (video == null) {
            return 4;
        }
        String lang = video.getIso6391();
        if (lang != null) {
            String lower = lang.trim().toLowerCase();
            if (lower.startsWith("vi")) {
                return 1;
            }
            if (lower.startsWith("en")) {
                return 2;
            }
            if (StringUtils.hasText(originalLanguage) && lower.startsWith(originalLanguage.trim().toLowerCase())) {
                return 3;
            }
        }
        // Name-based Vietnamese detection (e.g. "lồng tiếng", "thuyết minh", "tiếng việt")
        if (video.getName() != null) {
            String n = video.getName().toLowerCase();
            if (n.contains("tiếng việt") || n.contains("lồng tiếng") || n.contains("thuyết minh")) {
                return 1;
            }
        }
        return 4;
    }

    private static boolean hasOfficialKeyword(String name) {
        return name != null && name.toLowerCase().contains(OFFICIAL_TRAILER_KEYWORD);
    }

    private static Instant parsePublishedAt(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(dateStr.trim());
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(dateStr.trim()).toInstant();
            } catch (Exception e2) {
                return Instant.EPOCH;
            }
        }
    }
}
