package com.cinebook.service.impl;

import com.cinebook.dto.request.CreateShowtimeRequest;
import com.cinebook.dto.request.UpdateShowtimeRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.ShowtimeDetailResponse;
import com.cinebook.dto.response.ShowtimeSummaryResponse;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.enums.MovieStatus;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.ShowtimeMapper;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.CinemaRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.repository.specification.ShowtimeSpecification;
import com.cinebook.service.ShowtimeService;
import com.cinebook.service.scheduling.SchedulingValidationResult;
import com.cinebook.service.scheduling.SchedulingValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowtimeServiceImpl implements ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final CinemaRepository cinemaRepository;
    private final BookingRepository bookingRepository;
    private final ShowtimeMapper showtimeMapper;
    private final SchedulingValidationService validationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowtimeSummaryResponse> getPublicShowtimes(
            String movieId,
            String cinemaId,
            String auditoriumId,
            LocalDate date,
            ShowtimeFormat format,
            String language,
            Pageable pageable
    ) {
        Specification<Showtime> spec = Specification.where(ShowtimeSpecification.isPubliclyVisible())
                .and(ShowtimeSpecification.hasMovieId(movieId))
                .and(ShowtimeSpecification.hasCinemaId(cinemaId))
                .and(ShowtimeSpecification.hasAuditoriumId(auditoriumId))
                .and(ShowtimeSpecification.isOnDate(date))
                .and(ShowtimeSpecification.hasFormat(format))
                .and(ShowtimeSpecification.hasLanguage(language));

        Page<Showtime> page = showtimeRepository.findAll(spec, pageable);
        return PageResponse.of(page, showtimeMapper::toShowtimeSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowtimeDetailResponse getPublicShowtimeDetail(String id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with id: " + id));

        Movie movie = showtime.getMovie();
        Auditorium auditorium = showtime.getAuditorium();
        Cinema cinema = auditorium != null ? auditorium.getCinema() : null;

        if (showtime.getStatus() != ShowtimeStatus.SCHEDULED
                || movie == null || movie.getDeletedAt() != null || movie.getStatus() == MovieStatus.HIDDEN
                || auditorium == null || auditorium.getDeletedAt() != null || auditorium.getStatus() != AuditoriumStatus.ACTIVE
                || cinema == null || cinema.getDeletedAt() != null || cinema.getStatus() != CinemaStatus.ACTIVE) {
            throw new ResourceNotFoundException("Showtime not found with id: " + id);
        }

        return showtimeMapper.toShowtimeDetailResponse(showtime);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowtimeSummaryResponse> getAdminShowtimes(
            String movieId,
            String cinemaId,
            String auditoriumId,
            LocalDate date,
            ShowtimeStatus status,
            ShowtimeFormat format,
            String language,
            Pageable pageable
    ) {
        Specification<Showtime> spec = (root, query, cb) -> cb.conjunction();

        spec = spec.and(ShowtimeSpecification.hasMovieId(movieId))
                .and(ShowtimeSpecification.hasCinemaId(cinemaId))
                .and(ShowtimeSpecification.hasAuditoriumId(auditoriumId))
                .and(ShowtimeSpecification.isOnDate(date))
                .and(ShowtimeSpecification.hasStatus(status))
                .and(ShowtimeSpecification.hasFormat(format))
                .and(ShowtimeSpecification.hasLanguage(language));

        Page<Showtime> page = showtimeRepository.findAll(spec, pageable);
        return PageResponse.of(page, showtimeMapper::toShowtimeSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowtimeDetailResponse getAdminShowtimeDetail(String id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with id: " + id));

        return showtimeMapper.toShowtimeDetailResponse(showtime);
    }

    @Override
    @Transactional
    public ShowtimeDetailResponse createShowtime(CreateShowtimeRequest request) {
        Movie movie = validateAndGetMovie(request.getMovieId());
        Auditorium auditorium = validateAndGetAuditorium(request.getAuditoriumId());

        if (request.getBasePrice() == null || request.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Giá vé cơ bản không được âm!");
        }

        // Canonical calculation: Movie.durationMinutes is the ONLY source of truth
        int duration = movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 120;
        LocalDateTime endTime = validationService.calculateEndTime(request.getStartTime(), duration);

        LocalDate showDate = request.getStartTime().toLocalDate();
        List<Showtime> existing = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                auditorium.getId(), showDate.atStartOfDay(), showDate.atTime(LocalTime.MAX));

        SchedulingValidationResult valResult = validationService.validateSlot(
                movie, auditorium, request.getStartTime(), endTime, existing, null, null, null);

        if (!valResult.isValid()) {
            com.cinebook.dto.response.SchedulingConflictResponse conflict = valResult.getConflicts().get(0);
            if (conflict.getType() == com.cinebook.enums.SchedulingConflictType.SHOWTIME_OVERLAP
                    || conflict.getType() == com.cinebook.enums.SchedulingConflictType.TURNAROUND_VIOLATION
                    || conflict.getType() == com.cinebook.enums.SchedulingConflictType.AUDITORIUM_MAINTENANCE
                    || conflict.getType() == com.cinebook.enums.SchedulingConflictType.AUDITORIUM_DECOMMISSIONED) {
                throw new ConflictException(conflict.getMessage());
            }
            throw new BadRequestException(conflict.getMessage());
        }

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setAuditorium(auditorium);
        showtime.setFormat(request.getFormat() != null ? request.getFormat() : ShowtimeFormat.TWO_D);
        showtime.setLanguage(request.getLanguage() != null && !request.getLanguage().isBlank()
                ? request.getLanguage().trim()
                : (movie.getLanguage() != null ? movie.getLanguage() : "Vietnamese"));
        showtime.setSubtitle(request.getSubtitle() != null ? request.getSubtitle().trim() : null);
        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setBasePrice(request.getBasePrice());
        showtime.setStatus(request.getStatus() != null ? request.getStatus() : ShowtimeStatus.SCHEDULED);

        Showtime saved = showtimeRepository.save(showtime);
        log.info("Created showtime id={} for movie '{}' in auditorium '{}', duration={}m",
                saved.getId(), movie.getTitle(), auditorium.getName(), duration);
        return showtimeMapper.toShowtimeDetailResponse(saved);
    }

    @Override
    @Transactional
    public ShowtimeDetailResponse updateShowtime(String id, UpdateShowtimeRequest request) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with id: " + id));

        Movie movie = validateAndGetMovie(request.getMovieId());
        Auditorium auditorium = validateAndGetAuditorium(request.getAuditoriumId());

        if (request.getBasePrice() == null || request.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Giá vé cơ bản không được âm!");
        }

        // Canonical calculation: Movie.durationMinutes is the ONLY source of truth
        int duration = movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 120;
        LocalDateTime endTime = validationService.calculateEndTime(request.getStartTime(), duration);

        boolean hasBookings = bookingRepository.existsByShowtimeId(id);
        if (hasBookings) {
            boolean movieChanged = !showtime.getMovie().getId().equals(request.getMovieId());
            boolean audChanged = !showtime.getAuditorium().getId().equals(request.getAuditoriumId());
            boolean startChanged = !showtime.getStartTime().isEqual(request.getStartTime());
            boolean endChanged = !showtime.getEndTime().isEqual(endTime);

            if (movieChanged || audChanged || startChanged || endChanged) {
                throw new BadRequestException("Không thể thay đổi phim, phòng chiếu hoặc thời gian của lịch chiếu đã có vé đặt!");
            }
        }

        LocalDate showDate = request.getStartTime().toLocalDate();
        List<Showtime> existing = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                auditorium.getId(), showDate.atStartOfDay(), showDate.atTime(LocalTime.MAX));

        SchedulingValidationResult valResult = validationService.validateSlot(
                movie, auditorium, request.getStartTime(), endTime, existing, id, null, null);

        if (!valResult.isValid()) {
            com.cinebook.dto.response.SchedulingConflictResponse conflict = valResult.getConflicts().get(0);
            if (conflict.getType() == com.cinebook.enums.SchedulingConflictType.SHOWTIME_OVERLAP
                    || conflict.getType() == com.cinebook.enums.SchedulingConflictType.TURNAROUND_VIOLATION
                    || conflict.getType() == com.cinebook.enums.SchedulingConflictType.AUDITORIUM_MAINTENANCE
                    || conflict.getType() == com.cinebook.enums.SchedulingConflictType.AUDITORIUM_DECOMMISSIONED) {
                throw new ConflictException(conflict.getMessage());
            }
            throw new BadRequestException(conflict.getMessage());
        }

        showtime.setMovie(movie);
        showtime.setAuditorium(auditorium);
        showtime.setFormat(request.getFormat());
        showtime.setLanguage(request.getLanguage().trim());
        showtime.setSubtitle(request.getSubtitle() != null ? request.getSubtitle().trim() : null);
        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setBasePrice(request.getBasePrice());
        showtime.setStatus(request.getStatus());

        Showtime updated = showtimeRepository.save(showtime);
        log.info("Updated showtime id={}", updated.getId());
        return showtimeMapper.toShowtimeDetailResponse(updated);
    }

    @Override
    @Transactional
    public void deleteShowtime(String id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with id: " + id));

        boolean hasBookings = bookingRepository.existsByShowtimeId(id);

        if (hasBookings) {
            showtime.setStatus(ShowtimeStatus.CANCELLED);
            showtimeRepository.save(showtime);
            log.info("Cancelled showtime with existing bookings: id={}", id);
        } else {
            showtimeRepository.delete(showtime);
            log.info("Physically deleted showtime without bookings: id={}", id);
        }
    }

    private Movie validateAndGetMovie(String movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + movieId));

        if (movie.getDeletedAt() != null || movie.getStatus() == MovieStatus.ENDED || movie.getStatus() == MovieStatus.HIDDEN) {
            throw new BadRequestException("Không thể tạo lịch chiếu cho phim đã kết thúc hoặc đang ẩn!");
        }

        return movie;
    }

    private Auditorium validateAndGetAuditorium(String auditoriumId) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(auditoriumId)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + auditoriumId));

        if (auditorium.getStatus() == AuditoriumStatus.DECOMMISSIONED) {
            throw new ConflictException("Phòng chiếu đã ngừng hoạt động (DECOMMISSIONED)!");
        }

        if (auditorium.getStatus() == AuditoriumStatus.MAINTENANCE) {
            throw new ConflictException("Phòng chiếu đang bảo trì hoặc không khả dụng!");
        }

        if (auditorium.getStatus() != AuditoriumStatus.ACTIVE) {
            throw new BadRequestException("Phòng chiếu không ở trạng thái hoạt động!");
        }

        Cinema cinema = auditorium.getCinema();
        if (cinema == null || cinema.getDeletedAt() != null || cinema.getStatus() != CinemaStatus.ACTIVE) {
            throw new BadRequestException("Rạp chiếu không ở trạng thái hoạt động!");
        }

        return auditorium;
    }
}