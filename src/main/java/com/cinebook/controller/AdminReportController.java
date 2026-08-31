package com.cinebook.controller;

import com.cinebook.dto.response.*;
import com.cinebook.enums.ReportFormat;
import com.cinebook.enums.ReportGroupBy;
import com.cinebook.enums.ReportSortBy;
import com.cinebook.enums.ReportType;
import com.cinebook.exception.BadRequestException;
import com.cinebook.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Tag(name = "Admin Reporting & Dashboard", description = "Admin business analytics, revenue trends, KPIs, occupancy, and report export")
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportService reportService;

    @Operation(summary = "Xem tổng quan KPI bảng điều khiển (Dashboard summary)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);
        DashboardResponse response = reportService.getDashboardSummary(fromTime, toTime);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xem biểu đồ xu hướng doanh thu (Revenue trends)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/revenue")
    public ResponseEntity<List<RevenueTrendResponse>> getRevenueTrend(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "DAY") ReportGroupBy groupBy
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);
        List<RevenueTrendResponse> response = reportService.getRevenueTrend(fromTime, toTime, groupBy);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Báo cáo hiệu suất và xếp hạng phim (Movie report)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/movies")
    public ResponseEntity<List<MovieReportResponse>> getMovieReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "REVENUE") ReportSortBy sortBy,
            @RequestParam(required = false) Integer limit
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);
        List<MovieReportResponse> response = reportService.getMovieReport(fromTime, toTime, sortBy, limit);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Báo cáo hiệu suất và xếp hạng rạp (Cinema report)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/cinemas")
    public ResponseEntity<List<CinemaReportResponse>> getCinemaReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "REVENUE") ReportSortBy sortBy,
            @RequestParam(required = false) Integer limit
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);
        List<CinemaReportResponse> response = reportService.getCinemaReport(fromTime, toTime, sortBy, limit);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Báo cáo tỷ lệ lấp đầy theo suất chiếu (Showtime occupancy)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/showtimes/occupancy")
    public ResponseEntity<PageResponse<ShowtimeOccupancyResponse>> getShowtimeOccupancy(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String cinemaId,
            @RequestParam(required = false) String movieId,
            @RequestParam(required = false, defaultValue = "START_TIME") ReportSortBy sortBy,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);
        PageResponse<ShowtimeOccupancyResponse> response = reportService.getShowtimeOccupancy(
                fromTime, toTime, cinemaId, movieId, sortBy, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xem top suất chiếu có tỷ lệ lấp đầy cao nhất", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/showtimes/top-occupancy")
    public ResponseEntity<List<ShowtimeOccupancyResponse>> getTopOccupancyShowtimes(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);
        List<ShowtimeOccupancyResponse> response = reportService.getTopOccupancyShowtimes(fromTime, toTime, limit);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Thống kê đơn đặt vé (Booking statistics)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/bookings")
    public ResponseEntity<BookingStatisticsResponse> getBookingStatistics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);
        BookingStatisticsResponse response = reportService.getBookingStatistics(fromTime, toTime);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Thống kê người dùng (User statistics)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/users")
    public ResponseEntity<UserStatisticsResponse> getUserStatistics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);
        UserStatisticsResponse response = reportService.getUserStatistics(fromTime, toTime);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Thống kê giao dịch hoàn tiền (Refund statistics)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/refunds")
    public ResponseEntity<RefundStatisticsResponse> getRefundStatistics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);
        RefundStatisticsResponse response = reportService.getRefundStatistics(fromTime, toTime);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xuất báo cáo định dạng CSV hoặc Excel XLSX", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(required = false, defaultValue = "REVENUE") ReportType reportType,
            @RequestParam(required = false, defaultValue = "CSV") ReportFormat format,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) ReportGroupBy groupBy,
            @RequestParam(required = false) ReportSortBy sortBy,
            @RequestParam(required = false) String cinemaId,
            @RequestParam(required = false) String movieId,
            @RequestParam(required = false) Integer limit
    ) {
        LocalDateTime fromTime = parseFromDate(from);
        LocalDateTime toTime = parseToDate(to);

        byte[] data = reportService.exportReport(reportType, format, fromTime, toTime, groupBy, sortBy, cinemaId, movieId, limit);
        String filename = reportService.getExportFilename(reportType, format);

        MediaType mediaType = (format == ReportFormat.XLSX)
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv; charset=UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(data);
    }

    private LocalDateTime parseFromDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return null;
        try {
            if (dateStr.length() == 10) {
                return LocalDate.parse(dateStr).atStartOfDay();
            }
            return LocalDateTime.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Định dạng ngày bắt đầu không hợp lệ (hỗ trợ YYYY-MM-DD hoặc ISO-8601): " + dateStr);
        }
    }

    private LocalDateTime parseToDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return null;
        try {
            if (dateStr.length() == 10) {
                return LocalDate.parse(dateStr).atTime(23, 59, 59, 999999999);
            }
            return LocalDateTime.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Định dạng ngày kết thúc không hợp lệ (hỗ trợ YYYY-MM-DD hoặc ISO-8601): " + dateStr);
        }
    }
}

