package com.cinebook.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "movies_genres")
public class MovieGenre {

    @EmbeddedId
    private MovieGenreId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("movieId")
    @JoinColumn(
        name = "movie_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKmovies_gen915193")
    )
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("genreId")
    @JoinColumn(
        name = "genre_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKmovies_gen62893")
    )
    private Genre genre;
}