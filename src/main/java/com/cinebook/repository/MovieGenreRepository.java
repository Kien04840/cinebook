package com.cinebook.repository;

import com.cinebook.entity.MovieGenre;
import com.cinebook.entity.MovieGenreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieGenreRepository
        extends JpaRepository<MovieGenre, MovieGenreId> {

    List<MovieGenre> findByMovieId(String movieId);

    List<MovieGenre> findByGenreId(String genreId);

    boolean existsByMovieIdAndGenreId(
            String movieId,
            String genreId
    );

    void deleteByMovieId(String movieId);

    void deleteByGenreId(String genreId);
}