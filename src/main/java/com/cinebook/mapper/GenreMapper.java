package com.cinebook.mapper;

import com.cinebook.dto.response.GenreResponse;
import com.cinebook.entity.Genre;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class GenreMapper {

    public GenreResponse toGenreResponse(Genre genre) {
        if (genre == null) {
            return null;
        }

        return GenreResponse.builder()
                .id(genre.getId())
                .name(genre.getName())
                .description(genre.getDescription())
                .build();
    }

    public List<GenreResponse> toGenreResponseList(Collection<Genre> genres) {
        if (genres == null || genres.isEmpty()) {
            return Collections.emptyList();
        }

        return genres.stream()
                .map(this::toGenreResponse)
                .toList();
    }
}

