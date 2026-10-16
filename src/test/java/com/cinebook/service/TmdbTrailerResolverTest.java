package com.cinebook.service;

import com.cinebook.dto.tmdb.TmdbMovieDetailDto;
import com.cinebook.entity.Movie;
import com.cinebook.service.impl.TmdbTrailerResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TmdbTrailerResolver - Trailer Fallback & Invariant Protection Tests")
class TmdbTrailerResolverTest {

    private TmdbMovieDetailDto.TmdbVideoDto createVideo(
            String key,
            String site,
            String type,
            String lang,
            Boolean official,
            String name,
            String publishedAt
    ) {
        TmdbMovieDetailDto.TmdbVideoDto v = new TmdbMovieDetailDto.TmdbVideoDto();
        v.setKey(key);
        v.setSite(site);
        v.setType(type);
        v.setIso6391(lang);
        v.setOfficial(official);
        v.setName(name);
        v.setPublishedAt(publishedAt);
        return v;
    }

    @Test
    @DisplayName("Case 1: Có trailer tiếng Việt -> chọn tiếng Việt")
    void case1_VietnameseTrailer_PreferredOverEnglishAndOfficial() {
        TmdbMovieDetailDto.TmdbVideoDto viTrailer = createVideo(
                "vi-key-123", "YouTube", "Trailer", "vi", false, "Trailer Tiếng Việt", "2024-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto enTrailer = createVideo(
                "en-key-456", "YouTube", "Trailer", "en", true, "Official Trailer", "2024-02-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(null, "en", List.of(enTrailer, viTrailer));
        assertEquals("https://www.youtube.com/watch?v=vi-key-123", result);
    }

    @Test
    @DisplayName("Case 2: Không có vi, có en -> chọn en")
    void case2_NoVietnamese_EnglishTrailerChosen() {
        TmdbMovieDetailDto.TmdbVideoDto esTrailer = createVideo(
                "es-key-789", "YouTube", "Trailer", "es", true, "Trailer Oficial", "2024-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto enTrailer = createVideo(
                "en-key-456", "YouTube", "Trailer", "en", true, "Official Trailer", "2024-02-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(null, "es", List.of(esTrailer, enTrailer));
        assertEquals("https://www.youtube.com/watch?v=en-key-456", result);
    }

    @Test
    @DisplayName("Case 3: Không có vi, en, có trailer tiếng gốc -> chọn tiếng gốc")
    void case3_NoViNoEn_OriginalLanguageTrailerChosen() {
        TmdbMovieDetailDto.TmdbVideoDto jaTrailer = createVideo(
                "ja-key-111", "YouTube", "Trailer", "ja", true, "Japanese Trailer", "2024-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto frTrailer = createVideo(
                "fr-key-222", "YouTube", "Trailer", "fr", true, "Bande-annonce", "2024-01-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(null, "ja", List.of(frTrailer, jaTrailer));
        assertEquals("https://www.youtube.com/watch?v=ja-key-111", result);
    }

    @Test
    @DisplayName("Case 4: Không có cả 3 -> fallback trailer YouTube hợp lệ khác")
    void case4_NoViNoEnNoOriginal_OtherValidYouTubeTrailerChosen() {
        TmdbMovieDetailDto.TmdbVideoDto deTrailer = createVideo(
                "de-key-333", "YouTube", "Trailer", "de", true, "Deutscher Trailer", "2024-01-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(null, "ko", List.of(deTrailer));
        assertEquals("https://www.youtube.com/watch?v=de-key-333", result);
    }

    @Test
    @DisplayName("Case 5: TMDB không có video nào -> giữ trailer cũ trong DB")
    void case5_NoVideosFromTmdb_PreservesExistingTrailer() {
        Movie movie = new Movie();
        movie.setTrailerUrl("https://www.youtube.com/watch?v=old-preserved-key");

        String resultEmpty = TmdbTrailerResolver.resolveTrailerUrl(movie, "en", Collections.emptyList());
        assertEquals("https://www.youtube.com/watch?v=old-preserved-key", resultEmpty);

        String resultNull = TmdbTrailerResolver.resolveTrailerUrl(movie, "en", null);
        assertEquals("https://www.youtube.com/watch?v=old-preserved-key", resultNull);
    }

    @Test
    @DisplayName("Case 5b: Phim chưa có trailer và TMDB không có video -> trả về null")
    void case5b_NoVideosFromTmdb_NullIfNoExistingTrailer() {
        String result = TmdbTrailerResolver.resolveTrailerUrl(null, "en", Collections.emptyList());
        assertNull(result);
    }

    @Test
    @DisplayName("Case 6: TMDB chỉ có teaser và clip -> không chọn clip/teaser nếu đã có trailer cũ")
    void case6a_OnlyTeaserAndClip_PreservesExistingTrailer() {
        Movie movie = new Movie();
        movie.setTrailerUrl("https://www.youtube.com/watch?v=existing-trailer-key");

        TmdbMovieDetailDto.TmdbVideoDto teaser = createVideo(
                "teaser-key", "YouTube", "Teaser", "en", true, "Teaser 1", "2024-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto clip = createVideo(
                "clip-key", "YouTube", "Clip", "en", true, "Clip 1", "2024-01-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(movie, "en", List.of(clip, teaser));
        assertEquals("https://www.youtube.com/watch?v=existing-trailer-key", result);
    }

    @Test
    @DisplayName("Case 6b: Phim chưa có trailer và TMDB chỉ có clip/featurette -> không chọn clip/featurette")
    void case6b_OnlyClipsAndFeaturettes_NoExistingTrailer_ReturnsNull() {
        TmdbMovieDetailDto.TmdbVideoDto clip = createVideo(
                "clip-key", "YouTube", "Clip", "en", true, "Clip 1", "2024-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto featurette = createVideo(
                "feat-key", "YouTube", "Featurette", "en", true, "Behind the scenes", "2024-01-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(null, "en", List.of(clip, featurette));
        assertNull(result);
    }

    @Test
    @DisplayName("Case 6c: Phim chưa có trailer và TMDB chỉ có teaser -> fallback teaser hợp lệ")
    void case6c_OnlyTeaser_NoExistingTrailer_FallsBackToTeaser() {
        TmdbMovieDetailDto.TmdbVideoDto teaser = createVideo(
                "teaser-key-999", "YouTube", "Teaser", "en", true, "Teaser 1", "2024-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto clip = createVideo(
                "clip-key", "YouTube", "Clip", "en", true, "Clip 1", "2024-01-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(null, "en", List.of(clip, teaser));
        assertEquals("https://www.youtube.com/watch?v=teaser-key-999", result);
    }

    @Test
    @DisplayName("Case 7: Phim đang có trailer chính thức, sync mới trả về trailer không official -> không hạ cấp trailer")
    void case7_ExistingOfficialTrailer_SyncReturnsUnofficial_DoesNotDowngrade() {
        Movie movie = new Movie();
        movie.setTrailerUrl("https://www.youtube.com/watch?v=official-en-key");

        TmdbMovieDetailDto.TmdbVideoDto officialOld = createVideo(
                "official-en-key", "YouTube", "Trailer", "en", true, "Official Trailer", "2023-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto unofficialNew = createVideo(
                "unofficial-new-key", "YouTube", "Trailer", "en", false, "Fan Trailer", "2024-01-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(movie, "en", List.of(officialOld, unofficialNew));
        assertEquals("https://www.youtube.com/watch?v=official-en-key", result);
    }

    @Test
    @DisplayName("Case 7b: Phim có trailer tiếng Anh, sync mới trả về trailer tiếng Việt chính thức -> nâng cấp trailer")
    void case7b_ExistingEnglishTrailer_SyncReturnsVietnameseOfficial_Upgrades() {
        Movie movie = new Movie();
        movie.setTrailerUrl("https://www.youtube.com/watch?v=official-en-key");

        TmdbMovieDetailDto.TmdbVideoDto enTrailer = createVideo(
                "official-en-key", "YouTube", "Trailer", "en", true, "Official Trailer", "2023-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto viTrailer = createVideo(
                "official-vi-key", "YouTube", "Trailer", "vi", true, "Trailer Chính Thức", "2024-01-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(movie, "en", List.of(enTrailer, viTrailer));
        assertEquals("https://www.youtube.com/watch?v=official-vi-key", result);
    }

    @Test
    @DisplayName("Case 8: Nhiều trailer cùng ngôn ngữ -> chọn official trailer mới nhất / chuẩn nhất")
    void case8_MultipleTrailersSameLanguage_PrefersOfficialAndNewest() {
        TmdbMovieDetailDto.TmdbVideoDto oldOfficial = createVideo(
                "old-official", "YouTube", "Trailer", "en", true, "Teaser Trailer", "2023-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto newerOfficialWithTitle = createVideo(
                "new-official-main", "YouTube", "Trailer", "en", true, "Official Trailer", "2024-01-01T00:00:00Z"
        );
        TmdbMovieDetailDto.TmdbVideoDto fanTrailer = createVideo(
                "fan-trailer", "YouTube", "Trailer", "en", false, "Official Trailer Re-cut", "2024-05-01T00:00:00Z"
        );

        String result = TmdbTrailerResolver.resolveTrailerUrl(null, "en", List.of(fanTrailer, oldOfficial, newerOfficialWithTitle));
        assertEquals("https://www.youtube.com/watch?v=new-official-main", result);
    }

    @Test
    @DisplayName("extractYouTubeKey: Phân tích đúng nhiều dạng URL YouTube")
    void extractYouTubeKey_VariousUrlFormats() {
        assertEquals("abc123XYZ", TmdbTrailerResolver.extractYouTubeKey("https://www.youtube.com/watch?v=abc123XYZ"));
        assertEquals("abc123XYZ", TmdbTrailerResolver.extractYouTubeKey("https://www.youtube.com/watch?v=abc123XYZ&t=30s"));
        assertEquals("abc123XYZ", TmdbTrailerResolver.extractYouTubeKey("https://youtu.be/abc123XYZ?si=test"));
        assertEquals("abc123XYZ", TmdbTrailerResolver.extractYouTubeKey("https://www.youtube.com/embed/abc123XYZ"));
        assertEquals("abc123XYZ", TmdbTrailerResolver.extractYouTubeKey("abc123XYZ"));
        assertNull(TmdbTrailerResolver.extractYouTubeKey(null));
        assertNull(TmdbTrailerResolver.extractYouTubeKey("   "));
    }
}
