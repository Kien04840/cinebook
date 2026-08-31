package com.cinebook.service;

import com.cinebook.dto.response.*;
import com.cinebook.enums.ReportFormat;
import com.cinebook.enums.ReportGroupBy;
import com.cinebook.enums.ReportSortBy;
import com.cinebook.enums.ReportType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportService {

    DashboardResponse getDashboardSummary(LocalDateTime from, LocalDateTime to);

    List<RevenueTrendResponse> getRevenueTrend(LocalDateTime from, LocalDateTime to, ReportGroupBy groupBy);

    List<MovieReportResponse> getMovieReport(LocalDateTime from, LocalDateTime to, ReportSortBy sortBy, Integer limit);

    List<CinemaReportResponse> getCinemaReport(LocalDateTime from, LocalDateTime to, ReportSortBy sortBy, Integer limit);

    PageResponse<ShowtimeOccupancyResponse> getShowtimeOccupancy(
            LocalDateTime from,
            LocalDateTime to,
            String cinemaId,
            String movieId,
            ReportSortBy sortBy,
            Pageable pageable
    );

    List<ShowtimeOccupancyResponse> getTopOccupancyShowtimes(LocalDateTime from, LocalDateTime to, Integer limit);

    BookingStatisticsResponse getBookingStatistics(LocalDateTime from, LocalDateTime to);

    UserStatisticsResponse getUserStatistics(LocalDateTime from, LocalDateTime to);

    RefundStatisticsResponse getRefundStatistics(LocalDateTime from, LocalDateTime to);

    byte[] exportReport(
            ReportType reportType,
            ReportFormat format,
            LocalDateTime from,
            LocalDateTime to,
            ReportGroupBy groupBy,
            ReportSortBy sortBy,
            String cinemaId,
            String movieId,
            Integer limit
    );

    String getExportFilename(ReportType reportType, ReportFormat format);
}

