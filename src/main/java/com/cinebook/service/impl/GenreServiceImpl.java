package com.cinebook.service.impl;

import com.cinebook.dto.request.CreateGenreRequest;
import com.cinebook.dto.request.UpdateGenreRequest;
import com.cinebook.dto.response.GenreResponse;
import com.cinebook.entity.Genre;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.GenreMapper;
import com.cinebook.repository.GenreRepository;
import com.cinebook.repository.MovieGenreRepository;
import com.cinebook.service.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService {

    private final GenreRepository genreRepository;
    private final MovieGenreRepository movieGenreRepository;
    private final GenreMapper genreMapper;

    @Override
    @Transactional(readOnly = true)
    public List<GenreResponse> getAllGenres() {
        List<Genre> genres = genreRepository.findAll();
        return genreMapper.toGenreResponseList(genres);
    }

    @Override
    @Transactional(readOnly = true)
    public GenreResponse getGenreById(String id) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Genre not found with id: " + id));
        return genreMapper.toGenreResponse(genre);
    }

    @Override
    @Transactional
    public GenreResponse createGenre(CreateGenreRequest request) {
        String name = request.getName().trim();

        if (genreRepository.existsByName(name)) {
            throw new ConflictException("Genre with name '" + name + "' already exists");
        }

        Genre genre = new Genre();
        genre.setName(name);
        genre.setDescription(request.getDescription());

        Genre savedGenre = genreRepository.save(genre);
        return genreMapper.toGenreResponse(savedGenre);
    }

    @Override
    @Transactional
    public GenreResponse updateGenre(String id, UpdateGenreRequest request) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Genre not found with id: " + id));

        String newName = request.getName().trim();
        if (!newName.equalsIgnoreCase(genre.getName()) && genreRepository.existsByName(newName)) {
            throw new ConflictException("Genre with name '" + newName + "' already exists");
        }

        genre.setName(newName);
        genre.setDescription(request.getDescription());

        Genre updatedGenre = genreRepository.save(genre);
        return genreMapper.toGenreResponse(updatedGenre);
    }

    @Override
    @Transactional
    public void deleteGenre(String id) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Genre not found with id: " + id));

        movieGenreRepository.deleteByGenreId(id);
        genreRepository.delete(genre);
    }
}

