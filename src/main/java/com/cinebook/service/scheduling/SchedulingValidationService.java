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

/**
 * Service kiểm tra tính hợp lệ và phát hiện xung đột lịch chiếu (Showtime Scheduling Validation Engine).
 *
 * Chịu trách nhiệm:
 * 1. Tính toán thời gian kết thúc suất chiếu (endTime) dựa trên thời lượng thực tế của phim (Single Source of Truth).
 * 2. Tính toán khoảng thời gian chiếm dụng phòng chiếu thực tế (occupancyEnd = startTime + duration + turnaround).
 * 3. Kiểm tra tính hợp lệ về trạng thái nghiệp vụ: Phim khả dụng, Phòng chiếu ACTIVE, Rạp chiếu mở cửa.
 * 4. Kiểm tra giờ hoạt động của rạp (Operating Hours: openingTime ~ closingTime).
 * 5. Phát hiện xung đột phòng chiếu với các suất chiếu đã tồn tại:
 *    - Trùng lấn thời gian chiếu phim: [startA, endA) và [startB, endB) giao nhau khi startA < endB && startB < endA.
 *    - Vi phạm thời gian dọn dẹp phòng (Turnaround buffer violation): Suất chiếu mới chen vào trước khi phòng dọn dẹp xong,
 *      hoặc thời gian dọn dẹp của suất mới lấn sang suất chiếu kế tiếp.
 * 6. Chuẩn hóa/làm tròn mốc giờ bắt đầu ứng viên theo bước nhảy thời gian (Snap Interval).
 *
 * PHÂN BIỆT RÕ HAI KHÁI NIỆM TRỌNG TÂM CỦA THUẬT TOÁN XẾP LỊCH:
 * - Turnaround Minutes (Thời gian dọn phòng): Khoảng thời gian vật lý cần thiết giữa 2 suất chiếu để nhân viên dọn dẹp rác,
 *   kiểm tra hệ thống âm thanh/máy chiếu (thường từ 10 - 20 phút, mặc định 15 phút).
 * - Snap Interval (Bước nhảy làm tròn): Bước thời gian chuẩn hóa để giờ chiếu bắt đầu ở các mốc đẹp, dễ nhớ cho khán giả
 *   (ví dụ: 10 phút, 15 phút; nếu tính toán ứng viên ra 14:07 thì snap lên 14:15).
 * - QUY TẮC: Turnaround Minutes != Snap Interval (Không được nhầm lẫn giữa hai khái niệm này).
 */
@Service
public class SchedulingValidationService {

    /**
     * Tính thời gian kết thúc của suất chiếu (endTime) dựa trên thời lượng phim.
     *
     * Mục đích:
     * - Đảm bảo nguồn thời lượng phim là chân lý duy nhất (Single Source of Truth từ Movie.durationMinutes).
     * - endTime biểu diễn thời điểm bộ phim kết thúc chiếu trên màn ảnh (chưa bao gồm thời gian dọn phòng).
     *
     * Công thức:
     *   endTime = startTime + durationMinutes
     *
     * @param startTime Thời gian bắt đầu chiếu
     * @param durationMinutes Thời lượng phim tính bằng phút
     * @return Thời điểm kết thúc phim
     */
    public LocalDateTime calculateEndTime(LocalDateTime startTime, int durationMinutes) {
        if (startTime == null) {
            return null;
        }
        return startTime.plusMinutes(durationMinutes);
    }

    /**
     * Tính thời điểm phòng chiếu hoàn tất việc dọn dẹp và sẵn sàng đón lượt khách tiếp theo (occupancyEnd).
     *
     * Mục đích:
     * - Xác định khoảng thời gian phòng chiếu bị chiếm dụng thực tế bởi một suất chiếu (gồm cả thời gian chiếu + dọn phòng).
     *
     * Công thức:
     *   occupancyEnd = startTime + durationMinutes + turnaroundMinutes
     *
     * @param startTime Thời gian bắt đầu chiếu
     * @param durationMinutes Thời lượng phim (phút)
     * @param turnaroundMinutes Thời gian dọn phòng chiếu (phút)
     * @return Thời điểm phòng chiếu hoàn toàn giải phóng
     */
    public LocalDateTime calculateOccupancyEnd(LocalDateTime startTime, int durationMinutes, int turnaroundMinutes) {
        if (startTime == null) {
            return null;
        }
        return startTime.plusMinutes(durationMinutes).plusMinutes(turnaroundMinutes);
    }

    /**
     * Kiểm tra toàn diện một khung giờ chiếu đề xuất (slot) xem có thỏa mãn mọi điều kiện vận hành và không bị xung đột.
     *
     * Luồng kiểm tra:
     * 1. Kiểm tra trạng thái Phim: Phim phải tồn tại, chưa bị xóa mềm, không ở trạng thái ENDED/HIDDEN, thời lượng > 0.
     * 2. Kiểm tra Phòng chiếu: Phòng phải tồn tại, chưa bị xóa mềm, trạng thái ACTIVE (không DECOMMISSIONED hay MAINTENANCE).
     * 3. Kiểm tra Rạp chiếu: Rạp chứa phòng phải ACTIVE.
     * 4. Kiểm tra Thứ tự thời gian: startTime < endTime.
     * 5. Kiểm tra Giờ mở/đóng cửa rạp: startTime >= openingTime và endTime <= closingTime (trong cùng một ngày).
     * 6. Kiểm tra Xung đột với các suất chiếu đã có trong phòng:
     *    a. Trùng lấn giờ chiếu: startTime < existingEnd && existingStart < endTime.
     *    b. Vi phạm dọn dẹp trước suất: startTime < existingOccupiedUntil (bắt đầu khi phòng trước chưa dọn xong).
     *    c. Vi phạm dọn dẹp sau suất: candidateOccupiedUntil > existingStart (dọn dẹp lấn sang giờ bắt đầu của suất sau).
     *
     * @param movie Thông tin phim
     * @param auditorium Thông tin phòng chiếu
     * @param startTime Thời điểm bắt đầu đề xuất
     * @param endTime Thời điểm kết thúc đề xuất
     * @param existingShowtimes Danh sách các suất chiếu đã có trong phòng vào ngày đó
     * @param excludeShowtimeId ID suất chiếu cần bỏ qua khi kiểm tra (dùng khi cập nhật chính suất chiếu đó)
     * @param openingTimeOverride Giờ mở cửa ghi đè (nếu có)
     * @param closingTimeOverride Giờ đóng cửa ghi đè (nếu có)
     * @return Kết quả kiểm tra thành công hoặc danh sách chi tiết các xung đột (SchedulingConflictResponse)
     */
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

                // Kiểm tra trực tiếp trùng lấn giờ chiếu: [startA, endA) và [startB, endB) giao nhau khi startA < endB && startB < endA
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

                // Kiểm tra vi phạm khoảng đệm dọn phòng trước suất đề xuất (suất bắt đầu khi suất trước chưa dọn dẹp xong)
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

                // Kiểm tra vi phạm khoảng đệm dọn phòng sau suất đề xuất (thời gian dọn dẹp của suất mới lấn sang suất kế tiếp)
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

    /**
     * Làm tròn mốc thời gian lên (Ceiling / Snap Up) theo bước nhảy snapIntervalMinutes.
     *
     * Mục đích:
     * - Đảm bảo các suất chiếu bắt đầu ở những mốc giờ chuẩn hóa, dễ nhớ đối với khán giả
     *   (ví dụ: 14:00, 14:15, 14:30... thay vì các mốc lẻ như 14:07, 14:23).
     *
     * Thuật toán:
     * - Xóa bỏ giây và mili giây (làm tròn lên nếu giây > 0).
     * - Lấy số dư: remainder = minute % snapIntervalMinutes.
     * - Nếu remainder == 0: đã là mốc chuẩn, giữ nguyên.
     * - Nếu remainder > 0: cộng thêm (snapIntervalMinutes - remainder) phút để chạm mốc tiếp theo.
     *
     * @param time Mốc thời gian cần làm tròn
     * @param snapIntervalMinutes Bước nhảy làm tròn (ví dụ: 5, 10, 15 phút)
     * @return Mốc thời gian đã được làm tròn lên
     */
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