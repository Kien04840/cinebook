package com.cinebook.service.impl;

import com.cinebook.dto.request.*;
import com.cinebook.dto.response.*;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.*;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.ShowtimeMapper;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.CinemaRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.service.ShowtimeSchedulingService;
import com.cinebook.service.scheduling.GenerationPlanningResult;
import com.cinebook.service.scheduling.SchedulingValidationResult;
import com.cinebook.service.scheduling.SchedulingValidationService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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

    public static final double QUOTA_DEFICIT_WEIGHT = 1000.0;
    public static final double DISTRIBUTION_WEIGHT = 10.0;
    public static final double CONSECUTIVE_MOVIE_PENALTY = 350.0;
    public static final double SIMULTANEOUS_DUPLICATE_PENALTY = 120.0;
    public static final double CONCENTRATION_PENALTY = 50.0;

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final CinemaRepository cinemaRepository;
    private final SchedulingValidationService validationService;
    private final ShowtimeMapper showtimeMapper;

    public static final Comparator<Auditorium> AUDITORIUM_NATURAL_ORDER = (a1, a2) -> {
        if (a1 == a2) return 0;
        if (a1 == null) return 1;
        if (a2 == null) return -1;

        int nameComp = compareNatural(a1.getName(), a2.getName());
        if (nameComp != 0) {
            return nameComp;
        }

        String id1 = a1.getId() != null ? a1.getId() : "";
        String id2 = a2.getId() != null ? a2.getId() : "";
        return id1.compareTo(id2);
    };

    public static int compareNatural(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return 1;
        if (s2 == null) return -1;

        int i1 = 0, i2 = 0;
        int len1 = s1.length(), len2 = s2.length();

        while (i1 < len1 && i2 < len2) {
            char c1 = s1.charAt(i1);
            char c2 = s2.charAt(i2);

            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                int start1 = i1;
                while (i1 < len1 && Character.isDigit(s1.charAt(i1))) {
                    i1++;
                }
                int start2 = i2;
                while (i2 < len2 && Character.isDigit(s2.charAt(i2))) {
                    i2++;
                }

                String numStr1 = s1.substring(start1, i1);
                String numStr2 = s2.substring(start2, i2);

                String trim1 = numStr1.replaceFirst("^0+(?!$)", "");
                String trim2 = numStr2.replaceFirst("^0+(?!$)", "");

                if (trim1.length() != trim2.length()) {
                    return Integer.compare(trim1.length(), trim2.length());
                }
                int numComp = trim1.compareTo(trim2);
                if (numComp != 0) {
                    return numComp;
                }
                int lenComp = Integer.compare(numStr1.length(), numStr2.length());
                if (lenComp != 0) {
                    return lenComp;
                }
            } else {
                char lower1 = Character.toLowerCase(c1);
                char lower2 = Character.toLowerCase(c2);
                if (lower1 != lower2) {
                    return Character.compare(lower1, lower2);
                }
                i1++;
                i2++;
            }
        }

        return Integer.compare(len1 - i1, len2 - i2);
    }

    @Getter
    @Setter
    private static class InternalAuditoriumState {
        private final Auditorium auditorium;
        private LocalDateTime cursor;
        private String lastScheduledMovieId;
        private final Map<String, Integer> movieCounts = new HashMap<>();
        private final List<Showtime> existingShowtimes;
        private boolean exhausted = false;

        public InternalAuditoriumState(Auditorium auditorium, LocalDateTime cursor, List<Showtime> existingShowtimes) {
            this.auditorium = auditorium;
            this.cursor = cursor;
            this.existingShowtimes = existingShowtimes != null ? existingShowtimes : Collections.emptyList();
        }

        public int getMovieCount(String movieId) {
            return movieCounts.getOrDefault(movieId, 0);
        }

        public void incrementMovieCount(String movieId) {
            movieCounts.put(movieId, getMovieCount(movieId) + 1);
        }
    }

    public GenerationPlanningResult planGeneration(ShowtimeGenerationRequest request) {
        // 1. Normalize and validate movies input
        List<MovieGenerationConfigDto> movieConfigs = new ArrayList<>();
        if (request.getMovies() != null && !request.getMovies().isEmpty()) {
            Set<String> seenMovieIds = new HashSet<>();
            for (MovieGenerationConfigDto cfg : request.getMovies()) {
                if (cfg.getMovieId() == null || cfg.getMovieId().isBlank()) {
                    throw new BadRequestException("Mã phim trong danh sách không được để trống!");
                }
                if (cfg.getTargetScreeningsPerDay() == null || cfg.getTargetScreeningsPerDay() < 1) {
                    throw new BadRequestException("Số suất chiếu mục tiêu mỗi ngày phải lớn hơn hoặc bằng 1!");
                }
                if (!seenMovieIds.add(cfg.getMovieId())) {
                    throw new BadRequestException("Trùng lặp mã phim trong yêu cầu: " + cfg.getMovieId());
                }
                movieConfigs.add(cfg);
            }
        } else if (request.getMovieId() != null && !request.getMovieId().isBlank()) {
            // Legacy compatibility calculation based on operating window capacity
            Movie legacyMovie = movieRepository.findById(request.getMovieId())
                    .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + request.getMovieId()));
            LocalTime opening = request.getOpeningTime() != null ? request.getOpeningTime() : LocalTime.of(8, 0);
            LocalTime closing = request.getClosingTime() != null ? request.getClosingTime() : LocalTime.of(23, 0);
            long opMinutes = java.time.Duration.between(opening, closing).toMinutes();
            int duration = legacyMovie.getDurationMinutes() != null ? legacyMovie.getDurationMinutes() : 120;
            int turnaround = 15;
            int legacyTarget = Math.max(1, (int) ((opMinutes + turnaround) / (duration + turnaround)));
            movieConfigs.add(MovieGenerationConfigDto.builder()
                    .movieId(request.getMovieId())
                    .targetScreeningsPerDay(legacyTarget)
                    .format(request.getFormat())
                    .language(request.getLanguage())
                    .subtitle(request.getSubtitle())
                    .basePrice(request.getBasePrice())
                    .build());
        } else {
            throw new BadRequestException("Phải chỉ định danh sách phim (movies) hoặc mã phim (movieId)!");
        }

        // 2. Validate auditorium IDs (duplicates check)
        List<String> rawAudIds = request.getAuditoriumIds() != null ? request.getAuditoriumIds() : Collections.emptyList();
        if (rawAudIds.isEmpty()) {
            throw new BadRequestException("Phải chọn ít nhất một phòng chiếu!");
        }
        Set<String> seenAudIds = new HashSet<>();
        List<String> distinctAudIds = new ArrayList<>();
        for (String audId : rawAudIds) {
            if (audId == null || audId.isBlank()) continue;
            String trimmed = audId.trim();
            if (!seenAudIds.add(trimmed)) {
                throw new BadRequestException("Trùng lặp mã phòng chiếu trong yêu cầu: " + trimmed);
            }
            distinctAudIds.add(trimmed);
        }
        if (distinctAudIds.isEmpty()) {
            throw new BadRequestException("Phải chọn ít nhất một phòng chiếu hợp lệ!");
        }

        // 3. Validate dates
        LocalDate startDate = request.getStartDate();
        if (startDate == null) {
            throw new BadRequestException("Ngày bắt đầu không được để trống!");
        }
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : startDate;
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("Ngày kết thúc không được trước ngày bắt đầu!");
        }

        // 4. Validate operating hours
        if (request.getOpeningTime() != null && request.getClosingTime() != null) {
            if (!request.getOpeningTime().isBefore(request.getClosingTime())) {
                throw new BadRequestException("Giờ mở cửa phải trước giờ đóng cửa!");
            }
        }

        // 5. Fetch auditoriums
        List<Auditorium> validAuditoriums = new ArrayList<>();
        List<String> missingAudIds = new ArrayList<>();
        for (String audId : distinctAudIds) {
            Optional<Auditorium> opt = auditoriumRepository.findByIdAndDeletedAtIsNull(audId);
            if (opt.isPresent()) {
                validAuditoriums.add(opt.get());
            } else {
                missingAudIds.add(audId);
            }
        }
        validAuditoriums.sort(AUDITORIUM_NATURAL_ORDER);

        // 6. Fetch and validate movies
        Map<String, Movie> movieMap = new HashMap<>();
        List<SchedulingConflictResponse> initialConflicts = new ArrayList<>();
        for (MovieGenerationConfigDto cfg : movieConfigs) {
            Movie m = movieRepository.findById(cfg.getMovieId()).orElse(null);
            if (m == null || m.getDeletedAt() != null || m.getStatus() == MovieStatus.ENDED || m.getStatus() == MovieStatus.HIDDEN
                    || m.getDurationMinutes() == null || m.getDurationMinutes() <= 0) {
                initialConflicts.add(SchedulingConflictResponse.builder()
                        .type(SchedulingConflictType.MOVIE_NOT_AVAILABLE)
                        .message("Phim không khả dụng để lập lịch chiếu: " + (m != null ? m.getTitle() : cfg.getMovieId()))
                        .build());
            } else {
                movieMap.put(cfg.getMovieId(), m);
            }
        }

        List<ShowtimeSlotPreviewResponse> allCandidateSlots = new ArrayList<>();
        Map<String, Integer> totalScheduledPerMovie = new LinkedHashMap<>();
        for (MovieGenerationConfigDto cfg : movieConfigs) {
            totalScheduledPerMovie.put(cfg.getMovieId(), 0);
        }

        int totalDays = (int) (endDate.toEpochDay() - startDate.toEpochDay() + 1);
        boolean hadCapacityShortfall = false;
        boolean hadExistingConflict = false;
        boolean hadDurationConstraint = false;

        // 7. Timeline scheduling loop per day
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            final LocalDate currentDate = date;

            // Emit conflict records for missing auditoriums
            for (String missingId : missingAudIds) {
                allCandidateSlots.add(ShowtimeSlotPreviewResponse.builder()
                        .date(currentDate)
                        .auditoriumId(missingId)
                        .valid(false)
                        .conflicts(List.of(SchedulingConflictResponse.builder()
                                .type(SchedulingConflictType.AUDITORIUM_INACTIVE)
                                .auditoriumId(missingId)
                                .message("Phòng chiếu không tồn tại hoặc đã bị xóa!")
                                .build()))
                        .build());
            }

            // Filter active auditoriums for today
            List<InternalAuditoriumState> auditoriumStates = new ArrayList<>();
            for (Auditorium aud : validAuditoriums) {
                if (aud.getStatus() != AuditoriumStatus.ACTIVE) {
                    SchedulingConflictType cType = aud.getStatus() == AuditoriumStatus.MAINTENANCE
                            ? SchedulingConflictType.AUDITORIUM_MAINTENANCE
                            : (aud.getStatus() == AuditoriumStatus.DECOMMISSIONED
                                    ? SchedulingConflictType.AUDITORIUM_DECOMMISSIONED
                                    : SchedulingConflictType.AUDITORIUM_INACTIVE);

                    allCandidateSlots.add(ShowtimeSlotPreviewResponse.builder()
                            .date(currentDate)
                            .auditoriumId(aud.getId())
                            .auditoriumName(aud.getName())
                            .valid(false)
                            .conflicts(List.of(SchedulingConflictResponse.builder()
                                    .type(cType)
                                    .auditoriumId(aud.getId())
                                    .auditoriumName(aud.getName())
                                    .message("Phòng chiếu không ở trạng thái hoạt động: " + aud.getStatus())
                                    .build()))
                            .build());
                    continue;
                }

                Cinema cinema = aud.getCinema();
                if (cinema == null || cinema.getDeletedAt() != null || cinema.getStatus() != CinemaStatus.ACTIVE) {
                    allCandidateSlots.add(ShowtimeSlotPreviewResponse.builder()
                            .date(currentDate)
                            .auditoriumId(aud.getId())
                            .auditoriumName(aud.getName())
                            .valid(false)
                            .conflicts(List.of(SchedulingConflictResponse.builder()
                                    .type(SchedulingConflictType.CINEMA_INACTIVE)
                                    .auditoriumId(aud.getId())
                                    .auditoriumName(aud.getName())
                                    .message("Rạp chiếu không ở trạng thái hoạt động!")
                                    .build()))
                            .build());
                    continue;
                }

                LocalTime opening = request.getOpeningTime() != null ? request.getOpeningTime()
                        : (cinema.getOpeningTime() != null ? cinema.getOpeningTime() : LocalTime.of(8, 0));
                LocalDateTime startCursor = currentDate.atTime(opening);

                List<Showtime> existing = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                        aud.getId(), currentDate.atStartOfDay(), currentDate.atTime(LocalTime.MAX));

                auditoriumStates.add(new InternalAuditoriumState(aud, startCursor, existing));
            }

            // Fresh daily quota state
            Map<String, Integer> scheduledToday = new HashMap<>();
            for (MovieGenerationConfigDto cfg : movieConfigs) {
                scheduledToday.put(cfg.getMovieId(), 0);
            }

            List<ShowtimeSlotPreviewResponse> dateCandidateSlots = new ArrayList<>();

            // Dispatch loop for the day
            while (true) {
                boolean anyQuotaRemaining = false;
                for (MovieGenerationConfigDto cfg : movieConfigs) {
                    if (movieMap.containsKey(cfg.getMovieId()) && scheduledToday.getOrDefault(cfg.getMovieId(), 0) < cfg.getTargetScreeningsPerDay()) {
                        anyQuotaRemaining = true;
                        break;
                    }
                }

                List<InternalAuditoriumState> availableAuds = auditoriumStates.stream()
                        .filter(a -> !a.isExhausted())
                        .toList();

                if (!anyQuotaRemaining || availableAuds.isEmpty()) {
                    break;
                }

                // Pick auditorium with earliest cursor
                InternalAuditoriumState selectedAud = null;
                for (InternalAuditoriumState a : availableAuds) {
                    if (selectedAud == null || a.getCursor().isBefore(selectedAud.getCursor())) {
                        selectedAud = a;
                    }
                }

                if (selectedAud == null) {
                    break;
                }

                Auditorium aud = selectedAud.getAuditorium();
                LocalDateTime cursor = selectedAud.getCursor();
                int turnaround = aud.getTurnaroundMinutes() != null ? aud.getTurnaroundMinutes() : 15;
                int snap = request.getSnapIntervalMinutes() != null ? request.getSnapIntervalMinutes()
                        : (aud.getSnapIntervalMinutes() != null ? aud.getSnapIntervalMinutes() : 15);
                LocalTime closing = request.getClosingTime() != null ? request.getClosingTime()
                        : (aud.getCinema() != null && aud.getCinema().getClosingTime() != null
                                ? aud.getCinema().getClosingTime() : LocalTime.of(23, 0));

                if (cursor.toLocalDate().isAfter(currentDate) || cursor.toLocalTime().isAfter(closing)) {
                    selectedAud.setExhausted(true);
                    continue;
                }

                // Advance cursor past existing showtime if cursor is inside [exStart, exOccupiedUntil)
                boolean jumpedExisting = false;
                for (Showtime ex : selectedAud.getExistingShowtimes()) {
                    if (ex.getStatus() == ShowtimeStatus.CANCELLED) continue;
                    LocalDateTime exStart = ex.getStartTime();
                    LocalDateTime exEnd = ex.getEndTime();
                    LocalDateTime exOccupiedUntil = exEnd.plusMinutes(turnaround);
                    if (cursor.isBefore(exOccupiedUntil) && !cursor.isBefore(exStart)) {
                        selectedAud.setCursor(validationService.snapTimeUp(exOccupiedUntil, snap));
                        jumpedExisting = true;
                        hadExistingConflict = true;
                        break;
                    }
                }
                if (jumpedExisting) {
                    if (selectedAud.getCursor().toLocalDate().isAfter(currentDate) || selectedAud.getCursor().toLocalTime().isAfter(closing)) {
                        selectedAud.setExhausted(true);
                    }
                    continue;
                }

                cursor = selectedAud.getCursor();

                // Feasibility First: find movies that can fit at cursor
                List<MovieGenerationConfigDto> feasibleMovies = new ArrayList<>();
                for (MovieGenerationConfigDto cfg : movieConfigs) {
                    if (!movieMap.containsKey(cfg.getMovieId())) continue;
                    int scheduled = scheduledToday.getOrDefault(cfg.getMovieId(), 0);
                    if (scheduled >= cfg.getTargetScreeningsPerDay()) {
                        continue;
                    }

                    Movie m = movieMap.get(cfg.getMovieId());
                    int duration = m.getDurationMinutes();
                    LocalDateTime candidateStart = cursor;
                    LocalDateTime candidateEnd = candidateStart.plusMinutes(duration);

                    if (candidateEnd.toLocalDate().isAfter(currentDate) || candidateEnd.toLocalTime().isAfter(closing)) {
                        hadDurationConstraint = true;
                        continue;
                    }

                    // Check conflict with existing showtimes
                    boolean conflict = false;
                    for (Showtime ex : selectedAud.getExistingShowtimes()) {
                        if (ex.getStatus() == ShowtimeStatus.CANCELLED) continue;
                        LocalDateTime exStart = ex.getStartTime();
                        LocalDateTime exEnd = ex.getEndTime();
                        LocalDateTime exOccupied = exEnd.plusMinutes(turnaround);

                        // Direct overlap
                        if (candidateStart.isBefore(exEnd) && exStart.isBefore(candidateEnd)) {
                            conflict = true;
                            break;
                        }
                        // Turnaround before candidate
                        if ((candidateStart.isEqual(exEnd) || candidateStart.isAfter(exEnd)) && candidateStart.isBefore(exOccupied)) {
                            conflict = true;
                            break;
                        }
                        // Turnaround after candidate
                        LocalDateTime candidateOccupied = candidateEnd.plusMinutes(turnaround);
                        if ((exStart.isEqual(candidateEnd) || exStart.isAfter(candidateEnd)) && exStart.isBefore(candidateOccupied)) {
                            conflict = true;
                            break;
                        }
                    }
                    if (conflict) {
                        hadExistingConflict = true;
                        continue;
                    }

                    feasibleMovies.add(cfg);
                }

                if (feasibleMovies.isEmpty()) {
                    // No movie fits at current cursor. Check if there's a future existing showtime to jump past
                    LocalDateTime nextJump = null;
                    for (Showtime ex : selectedAud.getExistingShowtimes()) {
                        if (ex.getStatus() == ShowtimeStatus.CANCELLED) continue;
                        if (ex.getStartTime().isAfter(cursor)) {
                            LocalDateTime afterEx = validationService.snapTimeUp(ex.getEndTime().plusMinutes(turnaround), snap);
                            if (nextJump == null || afterEx.isBefore(nextJump)) {
                                nextJump = afterEx;
                            }
                        }
                    }
                    if (nextJump != null && !nextJump.toLocalDate().isAfter(currentDate) && !nextJump.toLocalTime().isAfter(closing)) {
                        selectedAud.setCursor(nextJump);
                    } else {
                        selectedAud.setExhausted(true);
                    }
                    continue;
                }

                // Score feasible movies
                MovieGenerationConfigDto bestMovie = null;
                double bestScore = -Double.MAX_VALUE;

                for (MovieGenerationConfigDto cfg : feasibleMovies) {
                    int target = cfg.getTargetScreeningsPerDay();
                    int scheduled = scheduledToday.getOrDefault(cfg.getMovieId(), 0);
                    int remaining = target - scheduled;

                    double deficitRatio = (double) remaining / target;
                    double score = deficitRatio * QUOTA_DEFICIT_WEIGHT;
                    score += remaining * DISTRIBUTION_WEIGHT;

                    // Anti-consecutive penalty
                    if (cfg.getMovieId().equals(selectedAud.getLastScheduledMovieId())) {
                        score -= CONSECUTIVE_MOVIE_PENALTY;
                    }

                    // Soft penalty for simultaneous start of same movie in another auditorium
                    final LocalDateTime slotStart = cursor;
                    final String selectedAudId = selectedAud.getAuditorium().getId();
                    boolean simultaneous = dateCandidateSlots.stream().anyMatch(slot ->
                            slot.isValid() &&
                            slot.getMovieId().equals(cfg.getMovieId()) &&
                            !slot.getAuditoriumId().equals(selectedAudId) &&
                            slot.getStartTime().isEqual(slotStart)
                    );
                    if (simultaneous) {
                        score -= SIMULTANEOUS_DUPLICATE_PENALTY;
                    }

                    // Concentration penalty
                    int countInRoom = selectedAud.getMovieCount(cfg.getMovieId());
                    score -= countInRoom * CONCENTRATION_PENALTY;

                    if (bestMovie == null || score > bestScore) {
                        bestScore = score;
                        bestMovie = cfg;
                    } else if (Math.abs(score - bestScore) < 1e-6) {
                        // Stable deterministic tie-breaker by movieId
                        if (cfg.getMovieId().compareTo(bestMovie.getMovieId()) < 0) {
                            bestScore = score;
                            bestMovie = cfg;
                        }
                    }
                }

                // Schedule best candidate
                Movie movie = movieMap.get(bestMovie.getMovieId());
                int duration = movie.getDurationMinutes();
                LocalDateTime candidateStart = cursor;
                LocalDateTime candidateEnd = candidateStart.plusMinutes(duration);

                ShowtimeSlotPreviewResponse slot = ShowtimeSlotPreviewResponse.builder()
                        .date(currentDate)
                        .auditoriumId(aud.getId())
                        .auditoriumName(aud.getName())
                        .movieId(movie.getId())
                        .movieTitle(movie.getTitle())
                        .movieDurationMinutes((short) duration)
                        .startTime(candidateStart)
                        .endTime(candidateEnd)
                        .format(bestMovie.getFormat() != null ? bestMovie.getFormat() : (request.getFormat() != null ? request.getFormat() : ShowtimeFormat.TWO_D))
                        .language(bestMovie.getLanguage() != null && !bestMovie.getLanguage().isBlank()
                                ? bestMovie.getLanguage().trim()
                                : (request.getLanguage() != null && !request.getLanguage().isBlank()
                                        ? request.getLanguage().trim()
                                        : (movie.getLanguage() != null ? movie.getLanguage() : "Vietnamese")))
                        .subtitle(bestMovie.getSubtitle() != null ? bestMovie.getSubtitle().trim() : (request.getSubtitle() != null ? request.getSubtitle().trim() : null))
                        .basePrice(bestMovie.getBasePrice() != null ? bestMovie.getBasePrice() : (request.getBasePrice() != null ? request.getBasePrice() : BigDecimal.ZERO))
                        .valid(true)
                        .conflicts(Collections.emptyList())
                        .build();

                dateCandidateSlots.add(slot);
                scheduledToday.put(bestMovie.getMovieId(), scheduledToday.getOrDefault(bestMovie.getMovieId(), 0) + 1);
                selectedAud.setLastScheduledMovieId(bestMovie.getMovieId());
                selectedAud.incrementMovieCount(bestMovie.getMovieId());

                LocalDateTime nextAvailable = candidateEnd.plusMinutes(turnaround);
                selectedAud.setCursor(validationService.snapTimeUp(nextAvailable, snap));
            }

            allCandidateSlots.addAll(dateCandidateSlots);

            // Accumulate daily counts into total
            for (Map.Entry<String, Integer> entry : scheduledToday.entrySet()) {
                totalScheduledPerMovie.put(entry.getKey(), totalScheduledPerMovie.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }

        // 8. Compile summaries, warnings, and quality indicators
        int totalRequested = 0;
        int totalScheduled = 0;
        List<MovieGenerationSummaryDto> movieSummaries = new ArrayList<>();

        for (MovieGenerationConfigDto cfg : movieConfigs) {
            int movieTotalTarget = cfg.getTargetScreeningsPerDay() * totalDays;
            int movieTotalScheduled = totalScheduledPerMovie.getOrDefault(cfg.getMovieId(), 0);
            int movieRemaining = Math.max(0, movieTotalTarget - movieTotalScheduled);

            totalRequested += movieTotalTarget;
            totalScheduled += movieTotalScheduled;

            Movie m = movieMap.get(cfg.getMovieId());
            movieSummaries.add(MovieGenerationSummaryDto.builder()
                    .movieId(cfg.getMovieId())
                    .movieTitle(m != null ? m.getTitle() : cfg.getMovieId())
                    .targetScreenings(movieTotalTarget)
                    .scheduledScreenings(movieTotalScheduled)
                    .remainingScreenings(movieRemaining)
                    .build());
        }

        int totalUnscheduled = Math.max(0, totalRequested - totalScheduled);
        List<String> warnings = new ArrayList<>();
        if (totalUnscheduled > 0) {
            hadCapacityShortfall = true;
            warnings.add(String.format("INSUFFICIENT_AUDITORIUM_CAPACITY: Không thể xếp thêm %d suất vì thời gian khả dụng của các phòng không đủ.", totalUnscheduled));
            if (hadExistingConflict) {
                warnings.add("EXISTING_SCHEDULE_CONSTRAINT: Một số khung giờ đã bị chiếm chỗ bởi các suất chiếu có sẵn.");
            }
            if (hadDurationConstraint) {
                warnings.add("MOVIE_DURATION_CONSTRAINT: Một số phim có thời lượng dài không thể vừa vào các khoảng trống còn lại trước giờ đóng cửa.");
            }
        }

        List<String> qualityIndicators = new ArrayList<>();
        if (totalUnscheduled == 0 && totalRequested > 0) {
            qualityIndicators.add(String.format("✓ Đã xếp đủ 100%% số suất chiếu theo yêu cầu (%d/%d suất)", totalScheduled, totalRequested));
        } else if (totalRequested > 0) {
            qualityIndicators.add(String.format("⚠ Còn thiếu %d suất chưa thể xếp do giới hạn công suất phòng chiếu", totalUnscheduled));
        }
        qualityIndicators.add("✓ Các phim được phân bổ xen kẽ nhịp nhàng giữa các phòng chiếu");
        qualityIndicators.add("✓ Không có xung đột với các suất chiếu đã tồn tại");

        return GenerationPlanningResult.builder()
                .candidateSlots(allCandidateSlots)
                .movieSummaries(movieSummaries)
                .totalRequested(totalRequested)
                .totalScheduled(totalScheduled)
                .totalUnscheduled(totalUnscheduled)
                .warnings(warnings)
                .conflicts(initialConflicts)
                .qualityIndicators(qualityIndicators)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShowtimeGenerationPreviewResponse previewGeneration(ShowtimeGenerationRequest request) {
        GenerationPlanningResult plan = planGeneration(request);

        int totalValid = (int) plan.getCandidateSlots().stream().filter(ShowtimeSlotPreviewResponse::isValid).count();
        int totalConflicted = plan.getCandidateSlots().size() - totalValid;

        return ShowtimeGenerationPreviewResponse.builder()
                .totalProposed(plan.getCandidateSlots().size())
                .totalValid(totalValid)
                .totalConflicted(totalConflicted)
                .totalRequested(plan.getTotalRequested())
                .totalScheduled(plan.getTotalScheduled())
                .totalUnscheduled(plan.getTotalUnscheduled())
                .movieSummaries(plan.getMovieSummaries())
                .warnings(plan.getWarnings())
                .qualityIndicators(plan.getQualityIndicators())
                .slots(plan.getCandidateSlots())
                .build();
    }

    @Override
    @Transactional
    public ShowtimeGenerationResultResponse generateShowtimes(ShowtimeGenerationRequest request) {
        GenerationPlanningResult plan = planGeneration(request);

        int totalCreated = 0;
        int totalSkipped = 0;
        int totalConflicted = 0;
        List<ShowtimeSummaryResponse> createdSummaries = new ArrayList<>();
        List<SchedulingConflictResponse> allConflicts = new ArrayList<>(plan.getConflicts());

        Map<String, Auditorium> auditoriumCache = new HashMap<>();
        Map<String, Movie> movieCache = new HashMap<>();

        for (ShowtimeSlotPreviewResponse candidate : plan.getCandidateSlots()) {
            if (!candidate.isValid()) {
                totalConflicted++;
                if (candidate.getConflicts() != null) {
                    allConflicts.addAll(candidate.getConflicts());
                }
                continue;
            }

            // Revalidate against DB state for concurrent changes
            Auditorium auditorium = auditoriumCache.computeIfAbsent(candidate.getAuditoriumId(),
                    id -> auditoriumRepository.findByIdAndDeletedAtIsNull(id).orElse(null));
            if (auditorium == null || auditorium.getStatus() != AuditoriumStatus.ACTIVE) {
                totalConflicted++;
                allConflicts.add(SchedulingConflictResponse.builder()
                        .type(SchedulingConflictType.AUDITORIUM_INACTIVE)
                        .auditoriumId(candidate.getAuditoriumId())
                        .message("Phòng chiếu không tồn tại hoặc đã bị ngừng hoạt động!")
                        .build());
                continue;
            }

            Movie movie = movieCache.computeIfAbsent(candidate.getMovieId(),
                    id -> movieRepository.findById(id).orElse(null));
            if (movie == null || movie.getDeletedAt() != null || movie.getStatus() == MovieStatus.ENDED || movie.getStatus() == MovieStatus.HIDDEN) {
                totalConflicted++;
                allConflicts.add(SchedulingConflictResponse.builder()
                        .type(SchedulingConflictType.MOVIE_NOT_AVAILABLE)
                        .message("Phim không khả dụng để tạo lịch chiếu: " + candidate.getMovieId())
                        .build());
                continue;
            }

            // Check duplicate / idempotency
            boolean exists = showtimeRepository.existsByMovieIdAndAuditoriumIdAndStartTimeAndStatusNot(
                    candidate.getMovieId(), candidate.getAuditoriumId(), candidate.getStartTime(), ShowtimeStatus.CANCELLED);
            if (exists) {
                totalSkipped++;
                continue;
            }

            // Revalidate slot against current DB showtimes in case concurrent changes occurred
            LocalDate showDate = candidate.getStartTime().toLocalDate();
            List<Showtime> dbExisting = showtimeRepository.findActiveByAuditoriumIdAndStartTimeBetweenOrderByStartTimeAsc(
                    auditorium.getId(), showDate.atStartOfDay(), showDate.atTime(LocalTime.MAX));

            SchedulingValidationResult valResult = validationService.validateSlot(
                    movie, auditorium, candidate.getStartTime(), candidate.getEndTime(), dbExisting, null, request.getOpeningTime(), request.getClosingTime());

            if (!valResult.isValid()) {
                totalConflicted++;
                allConflicts.addAll(valResult.getConflicts());
                continue;
            }

            Showtime showtime = new Showtime();
            showtime.setMovie(movie);
            showtime.setAuditorium(auditorium);
            showtime.setFormat(candidate.getFormat() != null ? candidate.getFormat() : ShowtimeFormat.TWO_D);
            showtime.setLanguage(candidate.getLanguage() != null && !candidate.getLanguage().isBlank()
                    ? candidate.getLanguage().trim()
                    : (movie.getLanguage() != null ? movie.getLanguage() : "Vietnamese"));
            showtime.setSubtitle(candidate.getSubtitle() != null ? candidate.getSubtitle().trim() : null);
            showtime.setStartTime(candidate.getStartTime());
            showtime.setEndTime(candidate.getEndTime());
            showtime.setBasePrice(candidate.getBasePrice() != null ? candidate.getBasePrice() : BigDecimal.ZERO);
            showtime.setStatus(ShowtimeStatus.SCHEDULED);

            Showtime saved = showtimeRepository.save(showtime);
            createdSummaries.add(showtimeMapper.toShowtimeSummaryResponse(saved));
            totalCreated++;
        }

        return ShowtimeGenerationResultResponse.builder()
                .totalCreated(totalCreated)
                .totalSkipped(totalSkipped)
                .totalConflicted(totalConflicted)
                .totalRequested(plan.getTotalRequested())
                .totalScheduled(totalCreated)
                .totalUnscheduled(Math.max(0, plan.getTotalRequested() - totalCreated))
                .movieSummaries(plan.getMovieSummaries())
                .warnings(plan.getWarnings())
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

    public List<ShowtimeSlotPreviewResponse> generateCandidateSlots(ShowtimeGenerationRequest request) {
        return planGeneration(request).getCandidateSlots();
    }
}
