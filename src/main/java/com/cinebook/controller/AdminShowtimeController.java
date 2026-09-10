package com.cinebook.controller;

import com.cinebook.dto.request.*;
import com.cinebook.dto.response.*;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.service.ShowtimeSchedulingService;
import com.cinebook.service.ShowtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Controller quản trị lập lịch và điều phối suất chiếu (Admin Showtime Scheduling Controller).
 *
 * Chịu trách nhiệm:
 * - Tra cứu và lọc danh sách suất chiếu của ban quản lý.
 * - Hiển thị bảng lịch chiếu trực quan (Calendar Grid) và cấu hình phòng/rạp.
 * - Tự động sinh lịch chiếu theo thuật toán heuristic thông minh (preview / execute).
 * - Sao chép lịch chiếu giữa các ngày (Copy Schedule).
 * - Xác thực và gợi ý khung giờ chiếu tối ưu (Validate Slot / Suggest Next Slot).
 * - Cập nhật, di chuyển hoặc hủy suất chiếu có kiểm tra giao dịch đặt vé.
 */
@Tag(name = "Admin Showtime", description = "Quản lý và lập lịch suất chiếu tự động cho Quản trị viên")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/showtimes")
@RequiredArgsConstructor
public class AdminShowtimeController {

    private final ShowtimeService showtimeService;
    private final ShowtimeSchedulingService schedulingService;

    @Operation(summary = "Tìm kiếm và lọc danh sách suất chiếu cho quản trị viên")
    @GetMapping
    public ResponseEntity<PageResponse<ShowtimeSummaryResponse>> getAdminShowtimes(
            @RequestParam(name = "movieId", required = false) String movieId,
            @RequestParam(name = "cinemaId", required = false) String cinemaId,
            @RequestParam(name = "auditoriumId", required = false) String auditoriumId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "status", required = false) ShowtimeStatus status,
            @RequestParam(name = "format", required = false) ShowtimeFormat format,
            @RequestParam(name = "language", required = false) String language,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<ShowtimeSummaryResponse> response = showtimeService.getAdminShowtimes(
                movieId, cinemaId, auditoriumId, date, status, format, language, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy bảng lịch chiếu trực quan dạng ma trận cho một cụm rạp")
    @GetMapping("/calendar")
    public ResponseEntity<CalendarScheduleResponse> getCalendarSchedule(
            @RequestParam(name = "cinemaId") String cinemaId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        CalendarScheduleResponse response = schedulingService.getCalendarSchedule(cinemaId, from, to);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy thông tin cấu hình vận hành và lập lịch của một cụm rạp")
    @GetMapping("/scheduling-config")
    public ResponseEntity<CinemaSchedulingConfigResponse> getCinemaSchedulingConfig(
            @RequestParam(name = "cinemaId") String cinemaId
    ) {
        CinemaSchedulingConfigResponse response = schedulingService.getCinemaSchedulingConfig(cinemaId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Phân tích các khoảng thời gian trống và chiếm dụng của phòng chiếu trong ngày")
    @GetMapping("/auditorium-availability")
    public ResponseEntity<AuditoriumAvailabilityResponse> getAuditoriumAvailability(
            @RequestParam(name = "auditoriumId") String auditoriumId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        AuditoriumAvailabilityResponse response = schedulingService.getAuditoriumAvailability(auditoriumId, date);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xem chi tiết một suất chiếu theo ID cho quản trị viên")
    @GetMapping("/{id}")
    public ResponseEntity<ShowtimeDetailResponse> getAdminShowtimeDetail(@PathVariable String id) {
        ShowtimeDetailResponse response = showtimeService.getAdminShowtimeDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Tạo mới một suất chiếu (endTime tự động tính từ thời lượng phim)")
    @PostMapping
    public ResponseEntity<ShowtimeDetailResponse> createShowtime(@Valid @RequestBody CreateShowtimeRequest request) {
        ShowtimeDetailResponse response = showtimeService.createShowtime(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Kiểm tra tính hợp lệ của một khung giờ chiếu đề xuất (không lưu vào DB)")
    @PostMapping("/validate")
    public ResponseEntity<ValidateShowtimeSlotResponse> validateSingleSlot(
            @Valid @RequestBody ValidateShowtimeSlotRequest request
    ) {
        ValidateShowtimeSlotResponse response = schedulingService.validateSingleSlot(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Gợi ý khung giờ chiếu khả dụng sớm nhất cho một bộ phim trong phòng chiếu")
    @PostMapping("/suggest-next-slot")
    public ResponseEntity<SuggestShowtimeSlotResponse> suggestNextSlot(
            @Valid @RequestBody SuggestShowtimeSlotRequest request
    ) {
        SuggestShowtimeSlotResponse response = schedulingService.suggestNextSlot(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Mô phỏng xem trước kết quả sinh lịch chiếu tự động theo quy tắc tối ưu")
    @PostMapping("/generate/preview")
    public ResponseEntity<ShowtimeGenerationPreviewResponse> previewGeneration(
            @Valid @RequestBody ShowtimeGenerationRequest request
    ) {
        ShowtimeGenerationPreviewResponse response = schedulingService.previewGeneration(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Thực thi thuật toán sinh lịch tự động và lưu các suất chiếu hợp lệ vào DB")
    @PostMapping("/generate")
    public ResponseEntity<ShowtimeGenerationResultResponse> generateShowtimes(
            @Valid @RequestBody ShowtimeGenerationRequest request
    ) {
        ShowtimeGenerationResultResponse response = schedulingService.generateShowtimes(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Sao chép toàn bộ lịch chiếu từ một ngày nguồn sang một ngày đích")
    @PostMapping("/copy")
    public ResponseEntity<CopyScheduleResultResponse> copySchedule(
            @Valid @RequestBody CopyScheduleRequest request
    ) {
        CopyScheduleResultResponse response = schedulingService.copySchedule(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cập nhật thông tin một suất chiếu đã có")
    @PutMapping("/{id}")
    public ResponseEntity<ShowtimeDetailResponse> updateShowtime(
            @PathVariable String id,
            @Valid @RequestBody UpdateShowtimeRequest request
    ) {
        ShowtimeDetailResponse response = showtimeService.updateShowtime(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Di chuyển suất chiếu sang phòng khác hoặc giờ chiếu khác")
    @PatchMapping("/{id}/schedule")
    public ResponseEntity<ShowtimeDetailResponse> moveShowtimeSchedule(
            @PathVariable String id,
            @Valid @RequestBody MoveShowtimeScheduleRequest request
    ) {
        ShowtimeDetailResponse response = showtimeService.moveShowtimeSchedule(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xóa hoặc hủy suất chiếu (kiểm tra an toàn nếu đã có vé hoặc ghế đang giữ)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShowtime(@PathVariable String id) {
        showtimeService.deleteShowtime(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Đồng bộ trạng thái vòng đời: tự động chuyển các suất chiếu đã qua thời gian kết thúc sang FINISHED")
    @PostMapping("/cleanup-finished")
    public ResponseEntity<java.util.Map<String, Object>> cleanupFinishedShowtimes() {
        int updated = showtimeService.cleanupFinishedShowtimes();
        return ResponseEntity.ok(java.util.Map.of(
                "updatedCount", updated,
                "message", "Đã cập nhật " + updated + " suất chiếu kết thúc thành FINISHED"
        ));
    }
}
