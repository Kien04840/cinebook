package com.cinebook.mapper;

import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.CinemaSummaryResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.ShowtimeDetailResponse;
import com.cinebook.dto.response.ShowtimeSummaryResponse;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShowtimeMapper {

    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final AuditoriumMapper auditoriumMapper;

    public ShowtimeSummaryResponse toShowtimeSummaryResponse(Showtime showtime) {
        if (showtime == null) {
            return null;
        }

        Movie movie = showtime.getMovie();
        Auditorium auditorium = showtime.getAuditorium();
        Cinema cinema = auditorium != null ? auditorium.getCinema() : null;

        return ShowtimeSummaryResponse.builder()
                .id(showtime.getId())
                .movieId(movie != null ? movie.getId() : null)
                .movieTitle(movie != null ? movie.getTitle() : null)
                .moviePosterUrl(movie != null ? movie.getPosterUrl() : null)
                .movieDurationMinutes(movie != null ? movie.getDurationMinutes() : null)
                .movieAgeRating(movie != null ? movie.getAgeRating() : null)
                .cinemaId(cinema != null ? cinema.getId() : null)
                .cinemaName(cinema != null ? cinema.getName() : null)
                .cinemaCity(cinema != null ? cinema.getCity() : null)
                .auditoriumId(auditorium != null ? auditorium.getId() : null)
                .auditoriumName(auditorium != null ? auditorium.getName() : null)
                .auditoriumType(auditorium != null ? auditorium.getType() : null)
                .format(showtime.getFormat())
                .language(showtime.getLanguage())
                .subtitle(showtime.getSubtitle())
                .startTime(showtime.getStartTime())
                .endTime(showtime.getEndTime())
                .basePrice(showtime.getBasePrice())
                .status(showtime.getStatus())
                .createdAt(showtime.getCreatedAt())
                .updatedAt(showtime.getUpdatedAt())
                .build();
    }

    public ShowtimeDetailResponse toShowtimeDetailResponse(Showtime showtime) {
        if (showtime == null) {
            return null;
        }

        MovieSummaryResponse movieResponse = movieMapper.toMovieSummaryResponse(showtime.getMovie());
        Auditorium auditorium = showtime.getAuditorium();
        AuditoriumResponse auditoriumResponse = auditoriumMapper.toAuditoriumResponse(auditorium);
        CinemaSummaryResponse cinemaResponse = auditorium != null ? cinemaMapper.toCinemaSummaryResponse(auditorium.getCinema()) : null;

        return ShowtimeDetailResponse.builder()
                .id(showtime.getId())
                .movie(movieResponse)
                .cinema(cinemaResponse)
                .auditorium(auditoriumResponse)
                .format(showtime.getFormat())
                .language(showtime.getLanguage())
                .subtitle(showtime.getSubtitle())
                .startTime(showtime.getStartTime())
                .endTime(showtime.getEndTime())
                .basePrice(showtime.getBasePrice())
                .status(showtime.getStatus())
                .createdAt(showtime.getCreatedAt())
                .updatedAt(showtime.getUpdatedAt())
                .build();
    }
}