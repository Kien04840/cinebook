package com.cinebook.service;

import com.cinebook.dto.request.CreateGenreRequest;
import com.cinebook.dto.request.UpdateGenreRequest;
import com.cinebook.dto.response.GenreResponse;

import java.util.List;

public interface GenreService {

    List<GenreResponse> getAllGenres();

    GenreResponse getGenreById(String id);

    GenreResponse createGenre(CreateGenreRequest request);

    GenreResponse updateGenre(String id, UpdateGenreRequest request);

    void deleteGenre(String id);
}

