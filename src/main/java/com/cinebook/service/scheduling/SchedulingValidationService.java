package com.cinebook.service.scheduling;

import com.cinebook.dto.response.SchedulingConflictResponse;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.enums.MovieStatus;
import com.cinebook.enums.SchedulingConflictType;
import com.cinebook.enums.ShowtimeStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SchedulingValidationService {

    public LocalDateTime calculateEndTime(LocalDateTime startTime, int durationMinutes) {
        if (startTime == null) {
            return null;
        }
        return startTime.plusMinutes(durationMinutes);
    }

    public LocalDateTime calculateOccupancyEnd(LocalDateTime startTime, int durationMinutes, int turnaroundMinutes) {
        if (startTime == null) {
            return null;
        }
        return startTime.plusMinutes(durationMinutes).plusMinutes(turnaroundMinutes);
    }

    public SchedulingValidationResult validateSlot(
            Movie movie,
            Auditorium auditorium,
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<Showtime> existingShowtimes,
            String excludeShowtimeId,
            LocalTime openingTimeOverride,
            LocalTime closingTimeOverride
    ) {
        List<SchedulingConflictResponse> conflicts = new ArrayList<>();

        // 1. Movie validation
        if (movie == null || movie.getDeletedAt() != null || movie.getStatus() == MovieStatus.ENDED || movie.getStatus() == MovieStatus.HIDDEN) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.MOVIE_NOT_AVAILABLE)
                    .auditoriumId(auditorium != null ? auditorium.getId() : null)
                    .auditoriumName(auditorium != null ? auditorium.getName() : null)
                    .message("Không thể tạo lịch chiếu cho phim đã kết thúc hoặc đang ẩn!")
                    .build());
            return SchedulingValidationResult.failed(conflicts);
        }

        if (movie.getDurationMinutes() == null || movie.getDurationMinutes() <= 0) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.MOVIE_NOT_AVAILABLE)
                    .auditoriumId(auditorium != null ? auditorium.getId() : null)
                    .auditoriumName(auditorium != null ? auditorium.getName() : null)
                    .message("Thời lượng phim không hợp lệ!")
                    .build());
            return SchedulingValidationResult.failed(conflicts);
        }

        // 2. Auditorium validation
        if (auditorium == null || auditorium.getDeletedAt() != null) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.AUDITORIUM_INACTIVE)
                    .message("Phòng chiếu không tồn tại hoặc đã bị xóa!")
                    .build());
            return SchedulingValidationResult.failed(conflicts);
        }

        if (auditorium.getStatus() == AuditoriumStatus.DECOMMISSIONED) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.AUDITORIUM_DECOMMISSIONED)
                    .auditoriumId(auditorium.getId())
                    .auditoriumName(auditorium.getName())
                    .message("Phòng chiếu đã ngừng hoạt động (DECOMMISSIONED)!")
                    .build());
            return SchedulingValidationResult.failed(conflicts);
        }

        if (auditorium.getStatus() == AuditoriumStatus.MAINTENANCE) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.AUDITORIUM_MAINTENANCE)
                    .auditoriumId(auditorium.getId())
                    .auditoriumName(auditorium.getName())
                    .message("Phòng chiếu đang bảo trì hoặc không khả dụng!")
                    .build());
            return SchedulingValidationResult.failed(conflicts);
        }

        if (auditorium.getStatus() != AuditoriumStatus.ACTIVE) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.AUDITORIUM_INACTIVE)
                    .auditoriumId(auditorium.getId())
                    .auditoriumName(auditorium.getName())
                    .message("Phòng chiếu không ở trạng thái hoạt động!")
                    .build());
            return SchedulingValidationResult.failed(conflicts);
        }

        // 3. Cinema validation
        Cinema cinema = auditorium.getCinema();
        if (cinema == null || cinema.getDeletedAt() != null || cinema.getStatus() != CinemaStatus.ACTIVE) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.CINEMA_INACTIVE)
                    .auditoriumId(auditorium.getId())
                    .auditoriumName(auditorium.getName())
                    .message("Rạp chiếu không ở trạng thái hoạt động!")
                    .build());
            return SchedulingValidationResult.failed(conflicts);
        }

        // 4. Time sequence
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.INVALID_TIME)
                    .auditoriumId(auditorium.getId())
                    .auditoriumName(auditorium.getName())
                    .message("Thời gian kết thúc phải lớn hơn thời gian bắt đầu!")
                    .build());
            return SchedulingValidationResult.failed(conflicts);
        }

        // 5. Operating hours
        LocalTime opening = openingTimeOverride != null ? openingTimeOverride : (cinema.getOpeningTime() != null ? cinema.getOpeningTime() : LocalTime.of(8, 0));
        LocalTime closing = closingTimeOverride != null ? closingTimeOverride : (cinema.getClosingTime() != null ? cinema.getClosingTime() : LocalTime.of(23, 0));

        if (startTime.toLocalTime().isBefore(opening)) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.OUTSIDE_OPERATING_HOURS)
                    .auditoriumId(auditorium.getId())
                    .auditoriumName(auditorium.getName())
                    .message(String.format("Thời gian bắt đầu (%s) sớm hơn giờ mở cửa rạp (%s)!", startTime.toLocalTime(), opening))
                    .build());
        }

        if (endTime.toLocalDate().isAfter(startTime.toLocalDate()) || endTime.toLocalTime().isAfter(closing)) {
            conflicts.add(SchedulingConflictResponse.builder()
                    .type(SchedulingConflictType.OUTSIDE_OPERATING_HOURS)
                    .auditoriumId(auditorium.getId())
                    .auditoriumName(auditorium.getName())
                    .message(String.format("Thời gian kết thúc (%s) muộn hơn giờ đóng cửa rạp (%s)!", endTime.toLocalTime(), closing))
                    .build());
        }

        // 6. Turnaround & Occupancy conflict check with existing showtimes
        int turnaround = auditorium.getTurnaroundMinutes() != null ? auditorium.getTurnaroundMinutes() : 15;

        if (existingShowtimes != null) {
            for (Showtime existing : existingShowtimes) {
                if (existing.getStatus() == ShowtimeStatus.CANCELLED) {
                    continue;
                }
                if (excludeShowtimeId != null && excludeShowtimeId.equals(existing.getId())) {
                    continue;
                }

                LocalDateTime existingStart = existing.getStartTime();
                LocalDateTime existingEnd = existing.getEndTime();
                LocalDateTime existingOccupiedUntil = existingEnd.plusMinutes(turnaround);

                // Check direct movie screening overlap: [startA, endA] and [startB, endB] overlap if startA < endB and startB < endA
                if (startTime.isBefore(existingEnd) && existingStart.isBefore(endTime)) {
                    conflicts.add(SchedulingConflictResponse.builder()
                            .type(SchedulingConflictType.SHOWTIME_OVERLAP)
                            .auditoriumId(auditorium.getId())
                            .auditoriumName(auditorium.getName())
                            .existingShowtimeId(existing.getId())
                            .conflictingStartTime(existingStart)
                            .conflictingEndTime(existingEnd)
                            .message("Phòng chiếu đã có lịch chiếu trong khoảng thời gian này!")
                            .build());
                    continue;
                }

                // Check turnaround buffer violation before candidate (candidate starts before existing room cleaning finishes)
                if ((startTime.isEqual(existingEnd) || startTime.isAfter(existingEnd)) && startTime.isBefore(existingOccupiedUntil)) {
                    conflicts.add(SchedulingConflictResponse.builder()
                            .type(SchedulingConflictType.TURNAROUND_VIOLATION)
                            .auditoriumId(auditorium.getId())
                            .auditoriumName(auditorium.getName())
                            .existingShowtimeId(existing.getId())
                            .conflictingStartTime(existingStart)
                            .conflictingEndTime(existingEnd)
                            .message(String.format("Phòng chiếu cần thời gian dọn dẹp (turnaround %d phút) đến %s!", turnaround, existingOccupiedUntil))
                            .build());
                    continue;
                }

                // Check turnaround buffer violation after candidate (candidate cleaning encroaches on next existing showtime)
                LocalDateTime candidateOccupiedUntil = endTime.plusMinutes(turnaround);
                if ((existingStart.isEqual(endTime) || existingStart.isAfter(endTime)) && existingStart.isBefore(candidateOccupiedUntil)) {
                    conflicts.add(SchedulingConflictResponse.builder()
                            .type(SchedulingConflictType.TURNAROUND_VIOLATION)
                            .auditoriumId(auditorium.getId())
                            .auditoriumName(auditorium.getName())
                            .existingShowtimeId(existing.getId())
                            .conflictingStartTime(existingStart)
                            .conflictingEndTime(existingEnd)
                            .message(String.format("Lịch chiếu này cần thời gian dọn dẹp đến %s, xung đột với suất chiếu tiếp theo lúc %s!", candidateOccupiedUntil, existingStart))
                            .build());
                }
            }
        }

        if (conflicts.isEmpty()) {
            return SchedulingValidationResult.success();
        }
        return SchedulingValidationResult.failed(conflicts);
    }

    public LocalDateTime snapTimeUp(LocalDateTime time, int snapIntervalMinutes) {
        if (time == null) {
            return null;
        }

        if (snapIntervalMinutes <= 1) {
            LocalDateTime clean = time.withSecond(0).withNano(0);
            if (time.getSecond() > 0 || time.getNano() > 0) {
                return clean.plusMinutes(1);
            }
            return clean;
        }

        LocalDateTime clean = time.withSecond(0).withNano(0);
        if (time.getSecond() > 0 || time.getNano() > 0) {
            clean = clean.plusMinutes(1);
        }

        int minute = clean.getMinute();
        int remainder = minute % snapIntervalMinutes;
        if (remainder == 0) {
            return clean;
        }

        int minutesToAdd = snapIntervalMinutes - remainder;
        return clean.plusMinutes(minutesToAdd);
    }
}