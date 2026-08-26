package com.cinebook.service.impl;

import com.cinebook.dto.request.CopyScheduleRequest;
import com.cinebook.dto.request.ShowtimeGenerationRequest;
import com.cinebook.dto.request.SuggestShowtimeSlotRequest;
import com.cinebook.dto.request.ValidateShowtimeSlotRequest;
import com.cinebook.dto.response.*;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.SchedulingConflictType;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.ShowtimeMapper;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.CinemaRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.service.ShowtimeSchedulingService;
import com.cinebook.service.scheduling.SchedulingValidationResult;
import com.cinebook.service.scheduling.SchedulingValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowtimeSchedulingServiceImpl implements ShowtimeSchedulingService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final CinemaRepository cinemaRepository;
    private final SchedulingValidationService validationService;
    private final ShowtimeMapper showtimeMapper;

    @Override
    @Transactional(readOnly = true)
    public ShowtimeGenerationPreviewResponse previewGeneration(ShowtimeGenerationRequest request) {
        List<ShowtimeSlotPreviewResponse> slots = generateCandidateSlots(request);

        int totalValid = (int) slots.stream().filter(ShowtimeSlotPreviewResponse::isValid).count();
        int totalConflicted = slots.size() - totalValid;

        return ShowtimeGenerationPreviewResponse.builder()
                .totalProposed(slots.size())
                .totalValid(totalValid)
                .totalConflicted(totalConflicted)
                .slots(slots)
                .build();
    }

    @Override
    @Transactional
    public ShowtimeGenerationResultResponse generateShowtimes(ShowtimeGenerationRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + request.getMovieId()));

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : startDate;

        int totalCreated = 0;
        int totalSkipped = 0;
        int totalConflicted = 0;
        List<ShowtimeSummaryResponse> createdSummaries = new ArrayList<>();
        List<SchedulingConflictResponse> allConflicts = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<String> auditoriumIds = request.getAuditoriumIds();
            for (int i = 0; i < auditoriumIds.size(); i++) {
                String audId = auditoriumIds.get(i);
                Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(audId).orElse(null);
                if (auditorium == null) {
                    continue;
                }

                int turnaround = auditorium.getTurnaroundMinutes() != null ? auditorium.getTurnaroundMinutes() : 15;
                int snap = request.getSnapIntervalMinutes() != null ? request.getSnapIntervalMinutes()
                        : (auditorium.getSnapIntervalMinutes() != null ? auditorium.getSnapIntervalMinutes() : 15);
                int stagger = request.getStaggerIntervalMinutes() != null ? request.getStaggerIntervalMinutes() : 0;

                Cinema cinema = auditorium.getCinema();
                LocalTime opening = request.getOpeningTime() != null ? request.getOpeningTime()
                        : (cinema != null && cinema.getOpeningTime() != null ? cinema.getOpeningTime() : LocalTime.of(8, 0));
                LocalTime closing = request.getClosingTime() != null ? request.getClosingTime()
                        : (cinema != null && cinema.getClosingTime() != null ? cinema.getClosingTime() : LocalTime.of(23, 0));

                LocalDateTime candidateStart = validationService.snapTimeUp(date.atTime(opening).plusMinutes((long) i * stagger), snap);
                int duration = movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 120;

                while (true) {
                    LocalDateTime candidateEnd = validationService.calculateEndTime(candidateStart, duration);
                    if (candidateEnd.toLocalDate().isAfter(date) || candidateEnd.toLocalTime().isAfter(closing)) {
                        break;
                    }

                    // Reload active showtimes directly from database
                    List<Showtime> existing = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                            audId, date.atStartOfDay(), date.atTime(LocalTime.MAX));

                    SchedulingValidationResult valResult = validationService.validateSlot(
                            movie, auditorium, candidateStart, candidateEnd, existing, null, request.getOpeningTime(), request.getClosingTime());

                    if (valResult.isValid()) {
                        // Check idempotency / duplicate
                        boolean exists = showtimeRepository.existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(
                                movie.getId(), audId, candidateStart, ShowtimeStatus.CANCELLED);

                        if (exists) {
                            totalSkipped++;
                        } else {
                            Showtime showtime = new Showtime();
                            showtime.setMovie(movie);
                            showtime.setAuditorium(auditorium);
                            showtime.setFormat(request.getFormat() != null ? request.getFormat() : ShowtimeFormat.TWO_D);
                            showtime.setLanguage(request.getLanguage() != null && !request.getLanguage().isBlank()
                                    ? request.getLanguage().trim()
                                    : (movie.getLanguage() != null ? movie.getLanguage() : "Vietnamese"));
                            showtime.setSubtitle(request.getSubtitle() != null ? request.getSubtitle().trim() : null);
                            showtime.setStartTime(candidateStart);
                            showtime.setEndTime(candidateEnd);
                            showtime.setBasePrice(request.getBasePrice() != null ? request.getBasePrice() : BigDecimal.ZERO);
                            showtime.setStatus(ShowtimeStatus.SCHEDULED);

                            Showtime saved = showtimeRepository.save(showtime);
                            createdSummaries.add(showtimeMapper.toShowtimeSummaryResponse(saved));
                            totalCreated++;
                        }

                        LocalDateTime nextStart = candidateEnd.plusMinutes(turnaround);
                        candidateStart = validationService.snapTimeUp(nextStart, snap);
                    } else {
                        totalConflicted++;
                        allConflicts.addAll(valResult.getConflicts());

                        // Advance cursor past conflict
                        LocalDateTime nextStart = candidateStart.plusMinutes(snap);
                        for (SchedulingConflictResponse conflict : valResult.getConflicts()) {
                            if (conflict.getConflictingEndTime() != null) {
                                LocalDateTime afterExisting = conflict.getConflictingEndTime().plusMinutes(turnaround);
                                if (afterExisting.isAfter(nextStart)) {
                                    nextStart = afterExisting;
                                }
                            }
                        }
                        candidateStart = validationService.snapTimeUp(nextStart, snap);
                    }
                }
            }
        }

        return ShowtimeGenerationResultResponse.builder()
                .totalCreated(totalCreated)
                .totalSkipped(totalSkipped)
                .totalConflicted(totalConflicted)
                .createdShowtimes(createdSummaries)
                .conflicts(allConflicts)
                .build();
    }

    @Override
    @Transactional
    public CopyScheduleResultResponse copySchedule(CopyScheduleRequest request) {
        LocalDate sourceDate = request.getSourceDate();
        LocalDate targetDate = request.getTargetDate();

        LocalDateTime srcStart = sourceDate.atStartOfDay();
        LocalDateTime srcEnd = sourceDate.atTime(LocalTime.MAX);

        List<Showtime> sourceShowtimes;
        if (request.getAuditoriumIds() != null && !request.getAuditoriumIds().isEmpty()) {
            sourceShowtimes = new ArrayList<>();
            for (String audId : request.getAuditoriumIds()) {
                sourceShowtimes.addAll(showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(audId, srcStart, srcEnd));
            }
        } else if (request.getCinemaId() != null) {
            sourceShowtimes = showtimeRepository.findCalendarShowtimes(request.getCinemaId(), srcStart, srcEnd);
        } else {
            sourceShowtimes = showtimeRepository.findByStartTimeBetween(srcStart, srcEnd);
        }

        int totalCopied = 0;
        int totalSkipped = 0;
        int totalConflicted = 0;
        List<ShowtimeSummaryResponse> createdSummaries = new ArrayList<>();
        List<SchedulingConflictResponse> conflicts = new ArrayList<>();

        for (Showtime src : sourceShowtimes) {
            if (src.getStatus() == ShowtimeStatus.CANCELLED) {
                continue;
            }

            Movie movie = src.getMovie();
            Auditorium auditorium = src.getAuditorium();
            if (movie == null || auditorium == null) {
                continue;
            }

            LocalDateTime targetStartTime = targetDate.atTime(src.getStartTime().toLocalTime());
            int duration = movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 120;
            LocalDateTime targetEndTime = validationService.calculateEndTime(targetStartTime, duration);

            List<Showtime> targetExisting = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                    auditorium.getId(), targetDate.atStartOfDay(), targetDate.atTime(LocalTime.MAX));

            SchedulingValidationResult valResult = validationService.validateSlot(
                    movie, auditorium, targetStartTime, targetEndTime, targetExisting, null, null, null);

            if (!valResult.isValid()) {
                totalConflicted++;
                conflicts.addAll(valResult.getConflicts());
                continue;
            }

            boolean duplicate = showtimeRepository.existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(
                    movie.getId(), auditorium.getId(), targetStartTime, ShowtimeStatus.CANCELLED);

            if (duplicate) {
                totalSkipped++;
                continue;
            }

            Showtime target = new Showtime();
            target.setMovie(movie);
            target.setAuditorium(auditorium);
            target.setFormat(src.getFormat());
            target.setLanguage(src.getLanguage());
            target.setSubtitle(src.getSubtitle());
            target.setStartTime(targetStartTime);
            target.setEndTime(targetEndTime);
            target.setBasePrice(src.getBasePrice());
            target.setStatus(ShowtimeStatus.SCHEDULED);

            Showtime saved = showtimeRepository.save(target);
            createdSummaries.add(showtimeMapper.toShowtimeSummaryResponse(saved));
            totalCopied++;
        }

        return CopyScheduleResultResponse.builder()
                .totalCopied(totalCopied)
                .totalSkipped(totalSkipped)
                .totalConflicted(totalConflicted)
                .createdShowtimes(createdSummaries)
                .conflicts(conflicts)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarScheduleResponse getCalendarSchedule(String cinemaId, LocalDate from, LocalDate to) {
        Cinema cinema = cinemaRepository.findByIdAndDeletedAtIsNull(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found with id: " + cinemaId));

        LocalDate startDate = from != null ? from : LocalDate.now();
        LocalDate endDate = to != null ? to : startDate.plusDays(7);

        LocalDateTime fromTime = startDate.atStartOfDay();
        LocalDateTime toTime = endDate.atTime(LocalTime.MAX);

        List<Showtime> showtimes = showtimeRepository.findCalendarShowtimes(cinemaId, fromTime, toTime);

        // Group showtimes by auditorium
        Map<String, List<ShowtimeSummaryResponse>> map = new LinkedHashMap<>();
        List<Auditorium> auditoriums = auditoriumRepository.findByCinemaIdAndDeletedAtIsNull(cinemaId);

        for (Auditorium aud : auditoriums) {
            map.put(aud.getId(), new ArrayList<>());
        }

        for (Showtime st : showtimes) {
            String audId = st.getAuditorium().getId();
            map.computeIfAbsent(audId, k -> new ArrayList<>()).add(showtimeMapper.toShowtimeSummaryResponse(st));
        }

        List<CalendarAuditoriumShowtimesResponse> audResponses = new ArrayList<>();
        for (Auditorium aud : auditoriums) {
            audResponses.add(CalendarAuditoriumShowtimesResponse.builder()
                    .auditoriumId(aud.getId())
                    .auditoriumName(aud.getName())
                    .auditoriumType(aud.getType())
                    .showtimes(map.getOrDefault(aud.getId(), Collections.emptyList()))
                    .build());
        }

        return CalendarScheduleResponse.builder()
                .cinemaId(cinema.getId())
                .cinemaName(cinema.getName())
                .from(startDate)
                .to(endDate)
                .auditoriums(audResponses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ValidateShowtimeSlotResponse validateSingleSlot(ValidateShowtimeSlotRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + request.getMovieId()));
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(request.getAuditoriumId())
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + request.getAuditoriumId()));

        int duration = movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 120;
        int turnaround = auditorium.getTurnaroundMinutes() != null ? auditorium.getTurnaroundMinutes() : 15;

        LocalDateTime calculatedEnd = validationService.calculateEndTime(request.getStartTime(), duration);
        LocalDateTime occupancyEnd = validationService.calculateOccupancyEnd(request.getStartTime(), duration, turnaround);

        LocalDate date = request.getStartTime().toLocalDate();
        List<Showtime> existing = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                auditorium.getId(), date.atStartOfDay(), date.atTime(LocalTime.MAX));

        SchedulingValidationResult valResult = validationService.validateSlot(
                movie, auditorium, request.getStartTime(), calculatedEnd, existing, request.getExcludeShowtimeId(), null, null);

        return ValidateShowtimeSlotResponse.builder()
                .valid(valResult.isValid())
                .calculatedStartTime(request.getStartTime())
                .calculatedEndTime(calculatedEnd)
                .movieDurationMinutes((short) duration)
                .occupancyEndTime(occupancyEnd)
                .conflicts(valResult.getConflicts())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SuggestShowtimeSlotResponse suggestNextSlot(SuggestShowtimeSlotRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + request.getMovieId()));
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(request.getAuditoriumId())
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + request.getAuditoriumId()));

        Cinema cinema = auditorium.getCinema();
        LocalTime opening = cinema != null && cinema.getOpeningTime() != null ? cinema.getOpeningTime() : LocalTime.of(8, 0);
        LocalTime closing = cinema != null && cinema.getClosingTime() != null ? cinema.getClosingTime() : LocalTime.of(23, 0);

        int duration = movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 120;
        int turnaround = auditorium.getTurnaroundMinutes() != null ? auditorium.getTurnaroundMinutes() : 15;
        int snap = request.getSnapIntervalMinutes() != null ? request.getSnapIntervalMinutes()
                : (auditorium.getSnapIntervalMinutes() != null ? auditorium.getSnapIntervalMinutes() : 15);

        LocalDate date = request.getRequestedStartTime().toLocalDate();
        LocalDateTime candidateStart = validationService.snapTimeUp(request.getRequestedStartTime(), snap);
        if (candidateStart.toLocalTime().isBefore(opening)) {
            candidateStart = date.atTime(opening);
        }

        List<Showtime> existing = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                auditorium.getId(), date.atStartOfDay(), date.atTime(LocalTime.MAX));

        while (true) {
            LocalDateTime candidateEnd = validationService.calculateEndTime(candidateStart, duration);
            if (candidateEnd.toLocalDate().isAfter(date) || candidateEnd.toLocalTime().isAfter(closing)) {
                return SuggestShowtimeSlotResponse.builder()
                        .available(false)
                        .movieDurationMinutes((short) duration)
                        .message("Không tìm thấy khung giờ phù hợp trong giờ hoạt động của rạp trên ngày này.")
                        .build();
            }

            SchedulingValidationResult valResult = validationService.validateSlot(
                    movie, auditorium, candidateStart, candidateEnd, existing, null, null, null);

            if (valResult.isValid()) {
                LocalDateTime occupancyEnd = validationService.calculateOccupancyEnd(candidateStart, duration, turnaround);
                return SuggestShowtimeSlotResponse.builder()
                        .available(true)
                        .suggestedStartTime(candidateStart)
                        .suggestedEndTime(candidateEnd)
                        .movieDurationMinutes((short) duration)
                        .occupancyEndTime(occupancyEnd)
                        .message("Tìm thấy khung giờ khả dụng.")
                        .build();
            }

            // Advance cursor
            LocalDateTime nextStart = candidateStart.plusMinutes(snap);
            for (SchedulingConflictResponse conflict : valResult.getConflicts()) {
                if (conflict.getConflictingEndTime() != null) {
                    LocalDateTime afterExisting = conflict.getConflictingEndTime().plusMinutes(turnaround);
                    if (afterExisting.isAfter(nextStart)) {
                        nextStart = afterExisting;
                    }
                }
            }
            candidateStart = validationService.snapTimeUp(nextStart, snap);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CinemaSchedulingConfigResponse getCinemaSchedulingConfig(String cinemaId) {
        Cinema cinema = cinemaRepository.findByIdAndDeletedAtIsNull(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found with id: " + cinemaId));

        List<Auditorium> auditoriums = auditoriumRepository.findByCinemaIdAndDeletedAtIsNull(cinemaId);
        List<AuditoriumSchedulingConfigResponse> audConfigs = auditoriums.stream()
                .map(a -> AuditoriumSchedulingConfigResponse.builder()
                        .id(a.getId())
                        .name(a.getName())
                        .type(a.getType())
                        .status(a.getStatus())
                        .turnaroundMinutes(a.getTurnaroundMinutes())
                        .snapIntervalMinutes(a.getSnapIntervalMinutes())
                        .build())
                .toList();

        return CinemaSchedulingConfigResponse.builder()
                .cinemaId(cinema.getId())
                .cinemaName(cinema.getName())
                .openingTime(cinema.getOpeningTime() != null ? cinema.getOpeningTime() : LocalTime.of(8, 0))
                .closingTime(cinema.getClosingTime() != null ? cinema.getClosingTime() : LocalTime.of(23, 0))
                .auditoriums(audConfigs)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuditoriumAvailabilityResponse getAuditoriumAvailability(String auditoriumId, LocalDate date) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(auditoriumId)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + auditoriumId));

        Cinema cinema = auditorium.getCinema();
        LocalDate queryDate = date != null ? date : LocalDate.now();
        LocalTime opening = cinema != null && cinema.getOpeningTime() != null ? cinema.getOpeningTime() : LocalTime.of(8, 0);
        LocalTime closing = cinema != null && cinema.getClosingTime() != null ? cinema.getClosingTime() : LocalTime.of(23, 0);
        int turnaround = auditorium.getTurnaroundMinutes() != null ? auditorium.getTurnaroundMinutes() : 15;

        List<Showtime> showtimes = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                auditoriumId, queryDate.atStartOfDay(), queryDate.atTime(LocalTime.MAX));

        List<TimeIntervalDto> intervals = new ArrayList<>();
        LocalDateTime cursor = queryDate.atTime(opening);
        LocalDateTime dayEnd = queryDate.atTime(closing);

        for (Showtime st : showtimes) {
            if (st.getStartTime().isAfter(cursor)) {
                intervals.add(TimeIntervalDto.builder()
                        .startTime(cursor)
                        .endTime(st.getStartTime())
                        .type("AVAILABLE")
                        .build());
            }

            intervals.add(TimeIntervalDto.builder()
                    .startTime(st.getStartTime())
                    .endTime(st.getEndTime())
                    .type("SHOWTIME")
                    .showtimeId(st.getId())
                    .movieTitle(st.getMovie() != null ? st.getMovie().getTitle() : null)
                    .build());

            LocalDateTime turnaroundEnd = st.getEndTime().plusMinutes(turnaround);
            intervals.add(TimeIntervalDto.builder()
                    .startTime(st.getEndTime())
                    .endTime(turnaroundEnd)
                    .type("TURNAROUND")
                    .showtimeId(st.getId())
                    .build());

            cursor = turnaroundEnd;
        }

        if (cursor.isBefore(dayEnd)) {
            intervals.add(TimeIntervalDto.builder()
                    .startTime(cursor)
                    .endTime(dayEnd)
                    .type("AVAILABLE")
                    .build());
        }

        return AuditoriumAvailabilityResponse.builder()
                .auditoriumId(auditorium.getId())
                .auditoriumName(auditorium.getName())
                .cinemaId(cinema != null ? cinema.getId() : null)
                .cinemaName(cinema != null ? cinema.getName() : null)
                .date(queryDate)
                .openingTime(opening)
                .closingTime(closing)
                .turnaroundMinutes(auditorium.getTurnaroundMinutes())
                .snapIntervalMinutes(auditorium.getSnapIntervalMinutes())
                .intervals(intervals)
                .build();
    }

    private List<ShowtimeSlotPreviewResponse> generateCandidateSlots(ShowtimeGenerationRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId()).orElse(null);
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : startDate;

        List<ShowtimeSlotPreviewResponse> slots = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<String> auditoriumIds = request.getAuditoriumIds();
            for (int i = 0; i < auditoriumIds.size(); i++) {
                String audId = auditoriumIds.get(i);
                Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(audId).orElse(null);
                if (auditorium == null) {
                    slots.add(ShowtimeSlotPreviewResponse.builder()
                            .date(date)
                            .auditoriumId(audId)
                            .valid(false)
                            .conflicts(List.of(SchedulingConflictResponse.builder()
                                    .type(SchedulingConflictType.AUDITORIUM_INACTIVE)
                                    .message("Phòng chiếu không tồn tại hoặc đã bị xóa!")
                                    .build()))
                            .build());
                    continue;
                }

                int turnaround = auditorium.getTurnaroundMinutes() != null ? auditorium.getTurnaroundMinutes() : 15;
                int snap = request.getSnapIntervalMinutes() != null ? request.getSnapIntervalMinutes()
                        : (auditorium.getSnapIntervalMinutes() != null ? auditorium.getSnapIntervalMinutes() : 15);
                int stagger = request.getStaggerIntervalMinutes() != null ? request.getStaggerIntervalMinutes() : 0;

                Cinema cinema = auditorium.getCinema();
                LocalTime opening = request.getOpeningTime() != null ? request.getOpeningTime()
                        : (cinema != null && cinema.getOpeningTime() != null ? cinema.getOpeningTime() : LocalTime.of(8, 0));
                LocalTime closing = request.getClosingTime() != null ? request.getClosingTime()
                        : (cinema != null && cinema.getClosingTime() != null ? cinema.getClosingTime() : LocalTime.of(23, 0));

                LocalDateTime candidateStart = validationService.snapTimeUp(date.atTime(opening).plusMinutes((long) i * stagger), snap);
                int duration = movie != null && movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 120;

                List<Showtime> existing = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                        audId, date.atStartOfDay(), date.atTime(LocalTime.MAX));

                while (true) {
                    LocalDateTime candidateEnd = validationService.calculateEndTime(candidateStart, duration);
                    if (candidateEnd.toLocalDate().isAfter(date) || candidateEnd.toLocalTime().isAfter(closing)) {
                        break;
                    }

                    SchedulingValidationResult valResult = validationService.validateSlot(
                            movie, auditorium, candidateStart, candidateEnd, existing, null, request.getOpeningTime(), request.getClosingTime());

                    slots.add(ShowtimeSlotPreviewResponse.builder()
                            .date(date)
                            .auditoriumId(auditorium.getId())
                            .auditoriumName(auditorium.getName())
                            .movieId(movie != null ? movie.getId() : request.getMovieId())
                            .movieTitle(movie != null ? movie.getTitle() : null)
                            .movieDurationMinutes((short) duration)
                            .startTime(candidateStart)
                            .endTime(candidateEnd)
                            .format(request.getFormat() != null ? request.getFormat() : ShowtimeFormat.TWO_D)
                            .language(request.getLanguage() != null ? request.getLanguage() : (movie != null ? movie.getLanguage() : "Vietnamese"))
                            .subtitle(request.getSubtitle())
                            .basePrice(request.getBasePrice() != null ? request.getBasePrice() : BigDecimal.ZERO)
                            .valid(valResult.isValid())
                            .conflicts(valResult.getConflicts())
                            .build());

                    if (valResult.isValid()) {
                        LocalDateTime nextStart = candidateEnd.plusMinutes(turnaround);
                        candidateStart = validationService.snapTimeUp(nextStart, snap);
                    } else {
                        // Advance cursor past conflict
                        LocalDateTime nextStart = candidateStart.plusMinutes(snap);
                        for (SchedulingConflictResponse conflict : valResult.getConflicts()) {
                            if (conflict.getConflictingEndTime() != null) {
                                LocalDateTime afterExisting = conflict.getConflictingEndTime().plusMinutes(turnaround);
                                if (afterExisting.isAfter(nextStart)) {
                                    nextStart = afterExisting;
                                }
                            }
                        }
                        candidateStart = validationService.snapTimeUp(nextStart, snap);
                    }
                }
            }
        }

        return slots;
    }
}
