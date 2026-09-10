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
import com.cinebook.service.PricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ShowtimeMapper {

    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final AuditoriumMapper auditoriumMapper;
    private final PricingService pricingService;

    public ShowtimeMapper(
            MovieMapper movieMapper,
            CinemaMapper cinemaMapper,
            AuditoriumMapper auditoriumMapper
    ) {
        this(movieMapper, cinemaMapper, auditoriumMapper, null);
    }

    @Autowired
    public ShowtimeMapper(
            MovieMapper movieMapper,
            CinemaMapper cinemaMapper,
            AuditoriumMapper auditoriumMapper,
            @Autowired(required = false) PricingService pricingService
    ) {
        this.movieMapper = movieMapper;
        this.cinemaMapper = cinemaMapper;
        this.auditoriumMapper = auditoriumMapper;
        this.pricingService = pricingService;
    }

    public ShowtimeSummaryResponse toShowtimeSummaryResponse(Showtime showtime) {
        if (showtime == null) {
            return null;
        }

        BigDecimal minPrice = null;
        if (pricingService != null) {
            try {
                minPrice = pricingService.calculateMinimumTicketPrice(showtime);
            } catch (Exception ignored) {
                minPrice = showtime.getBasePrice();
            }
        }
        if (minPrice == null) {
            minPrice = showtime.getBasePrice();
        }

        return toShowtimeSummaryResponse(showtime, minPrice);
    }

    public ShowtimeSummaryResponse toShowtimeSummaryResponse(Showtime showtime, BigDecimal minPrice) {
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
                .minPrice(minPrice != null ? minPrice : showtime.getBasePrice())
                .status(showtime.getStatus())
                .createdAt(showtime.getCreatedAt())
                .updatedAt(showtime.getUpdatedAt())
                .build();
    }

    public ShowtimeDetailResponse toShowtimeDetailResponse(Showtime showtime) {
        return toShowtimeDetailResponse(showtime, null);
    }

    public ShowtimeDetailResponse toShowtimeDetailResponse(Showtime showtime, com.cinebook.dto.response.TicketPricingBreakdown pricingBreakdown) {
        if (showtime == null) {
            return null;
        }

        MovieSummaryResponse movieResponse = movieMapper.toMovieSummaryResponse(showtime.getMovie());
        Auditorium auditorium = showtime.getAuditorium();
        AuditoriumResponse auditoriumResponse = auditoriumMapper.toAuditoriumResponse(auditorium);
        CinemaSummaryResponse cinemaResponse = auditorium != null ? cinemaMapper.toCinemaSummaryResponse(auditorium.getCinema()) : null;

        java.math.BigDecimal dayModifier = pricingBreakdown != null ? pricingBreakdown.getDayModifier() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal timeSlotModifier = pricingBreakdown != null ? pricingBreakdown.getTimeSlotModifier() : java.math.BigDecimal.ZERO;

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
                .dayModifier(dayModifier)
                .timeSlotModifier(timeSlotModifier)
                .pricingBreakdown(pricingBreakdown)
                .status(showtime.getStatus())
                .createdAt(showtime.getCreatedAt())
                .updatedAt(showtime.getUpdatedAt())
                .build();
    }
}