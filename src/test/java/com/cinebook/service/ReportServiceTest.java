package com.cinebook.service;

import com.cinebook.dto.response.*;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.BadRequestException;
import com.cinebook.repository.*;
import com.cinebook.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReportServiceImpl reportService;

    private LocalDateTime from;
    private LocalDateTime to;

    @BeforeEach
    void setUp() {
        from = LocalDateTime.of(2026, 8, 1, 0, 0, 0);
        to = LocalDateTime.of(2026, 8, 31, 23, 59, 59);
    }

    @Test
    @DisplayName("Dashboard: Calculate accurate financial, ticket, booking, user and operational KPIs")
    void getDashboardSummary_CalculatesAllMetricsCorrectly() {
        // Arrange
        when(paymentRepository.findGrossRevenueBetween(any(), any())).thenReturn(new BigDecimal("1000000.00"));
        when(refundRepository.findTotalRefundAmountBetween(any(), any())).thenReturn(new BigDecimal("100000.00"));

        when(ticketRepository.countTicketKpiBetween(any(), any())).thenReturn(new Object[]{10L, 1L});
        when(bookingRepository.countBookingKpiBetween(any(), any())).thenReturn(new Object[]{8L, 5L, 1L, 1L, 1L});
        when(userRepository.countUserKpiBetween(any(), any())).thenReturn(new Object[]{50L, 10L, 48L, 2L});
        when(showtimeRepository.findOperationsKpiNative(any(), any())).thenReturn(new Object[]{0L, 0L, 0L});

        // Act
        DashboardResponse response = reportService.getDashboardSummary(from, to);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getFinancial().getGrossRevenue()).isEqualByComparingTo("1000000.00");
        assertThat(response.getFinancial().getRefundAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.getFinancial().getNetRevenue()).isEqualByComparingTo("900000.00");

        assertThat(response.getTickets().getGrossTicketsSold()).isEqualTo(10L);
        assertThat(response.getTickets().getRefundedTickets()).isEqualTo(1L);
        assertThat(response.getTickets().getNetTicketsSold()).isEqualTo(9L);

        assertThat(response.getBookings().getTotalBookings()).isEqualTo(8L);
        assertThat(response.getBookings().getPaidBookings()).isEqualTo(5L);
        assertThat(response.getBookings().getRefundedBookings()).isEqualTo(1L);

        assertThat(response.getUsers().getTotalUsers()).isEqualTo(50L);
        assertThat(response.getUsers().getNewUsersInPeriod()).isEqualTo(10L);
        assertThat(response.getUsers().getActiveUsers()).isEqualTo(48L);
        assertThat(response.getUsers().getBlockedUsers()).isEqualTo(2L);

        assertThat(response.getOperations().getTotalShowtimes()).isEqualTo(0L);
        assertThat(response.getOperations().getAverageOccupancyRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Validation: Throws BadRequestException when from > to")
    void getDashboardSummary_FromAfterTo_ThrowsBadRequestException() {
        LocalDateTime invalidFrom = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime invalidTo = LocalDateTime.of(2026, 8, 1, 0, 0);

        assertThatThrownBy(() -> reportService.getDashboardSummary(invalidFrom, invalidTo))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ngày bắt đầu không được lớn hơn ngày kết thúc.");
    }

    @Test
    @DisplayName("Revenue Trend: Accumulates daily trends and calculates net revenue correctly")
    void getRevenueTrend_DailyGrouping_CalculatesAccurately() {
        // Arrange
        when(paymentRepository.findGrossRevenueTrendNative(any(), any(), eq("%Y-%m-%d")))
                .thenReturn(List.<Object[]>of(new Object[]{"2026-08-10", new BigDecimal("200000.00")}));
        when(refundRepository.findRefundTrendNative(any(), any(), eq("%Y-%m-%d")))
                .thenReturn(List.<Object[]>of(new Object[]{"2026-08-10", new BigDecimal("50000.00")}));
        when(ticketRepository.findTicketTrendNative(any(), any(), eq("%Y-%m-%d")))
                .thenReturn(Collections.emptyList());

        // Act
        List<RevenueTrendResponse> trend = reportService.getRevenueTrend(from, to, ReportGroupBy.DAY);

        // Assert
        assertThat(trend).hasSize(1);
        RevenueTrendResponse item = trend.get(0);
        assertThat(item.getPeriod()).isEqualTo("2026-08-10");
        assertThat(item.getGrossRevenue()).isEqualByComparingTo("200000.00");
        assertThat(item.getRefundAmount()).isEqualByComparingTo("50000.00");
        assertThat(item.getNetRevenue()).isEqualByComparingTo("150000.00");
    }

    @Test
    @DisplayName("Movie Report: Calculates movie ranking by Net Revenue and Net Tickets")
    void getMovieReport_RankingAndLimits() {
        // Arrange
        when(paymentRepository.findMovieRevenueReportNative(any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"m-1", "Movie A", null, new BigDecimal("500000.00"), 1L},
                        new Object[]{"m-2", "Movie B", null, new BigDecimal("300000.00"), 1L}
                ));
        when(refundRepository.findMovieRefundReportNative(any(), any()))
                .thenReturn(Collections.emptyList());
        when(ticketRepository.findMovieTicketReportNative(any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        List<MovieReportResponse> report = reportService.getMovieReport(from, to, ReportSortBy.REVENUE, 10);

        // Assert
        assertThat(report).hasSize(2);
        assertThat(report.get(0).getMovieTitle()).isEqualTo("Movie A");
        assertThat(report.get(0).getRank()).isEqualTo(1);
        assertThat(report.get(0).getNetRevenue()).isEqualByComparingTo("500000.00");
        assertThat(report.get(0).getTotalRevenue()).isEqualByComparingTo("500000.00");
        assertThat(report.get(0).getTicketsSold()).isEqualTo(0L);
        assertThat(report.get(0).getTotalBookings()).isEqualTo(1L);

        assertThat(report.get(1).getMovieTitle()).isEqualTo("Movie B");
        assertThat(report.get(1).getRank()).isEqualTo(2);
        assertThat(report.get(1).getNetRevenue()).isEqualByComparingTo("300000.00");
        assertThat(report.get(1).getTotalRevenue()).isEqualByComparingTo("300000.00");
        assertThat(report.get(1).getTicketsSold()).isEqualTo(0L);
        assertThat(report.get(1).getTotalBookings()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Cinema Report: Calculates cinema rankings and populates totalRevenue, ticketsSold, totalShowtimes")
    void getCinemaReport_CalculatesTotalsAndRankings() {
        // Arrange
        when(paymentRepository.findCinemaRevenueReportNative(any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"cin-1", "Cinema Alpha", "Hà Nội", new BigDecimal("1000000.00"), 1L}
                ));
        when(refundRepository.findCinemaRefundReportNative(any(), any()))
                .thenReturn(Collections.emptyList());
        when(ticketRepository.findCinemaTicketReportNative(any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        List<CinemaReportResponse> report = reportService.getCinemaReport(from, to, ReportSortBy.REVENUE, 10);

        // Assert
        assertThat(report).hasSize(1);
        CinemaReportResponse res = report.get(0);
        assertThat(res.getCinemaName()).isEqualTo("Cinema Alpha");
        assertThat(res.getCity()).isEqualTo("Hà Nội");
        assertThat(res.getTotalRevenue()).isEqualByComparingTo("1000000.00");
        assertThat(res.getTicketsSold()).isEqualTo(0L);
        assertThat(res.getTotalShowtimes()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Occupancy: Calculates capacity, occupied seats, and protects against division by zero")
    void getShowtimeOccupancy_CalculatesOccupancyRate_AndHandlesZeroCapacity() {
        // Arrange
        Cinema cinema = new Cinema();
        cinema.setId(UUID.randomUUID().toString());
        cinema.setName("Cinema Test");

        Auditorium aud = new Auditorium();
        aud.setId(UUID.randomUUID().toString());
        aud.setName("Auditorium 1");
        aud.setCinema(cinema);

        Movie movie = new Movie();
        movie.setId(UUID.randomUUID().toString());
        movie.setTitle("Avengers");

        Showtime st1 = new Showtime();
        st1.setId(UUID.randomUUID().toString());
        st1.setAuditorium(aud);
        st1.setMovie(movie);
        st1.setStartTime(LocalDateTime.of(2026, 8, 15, 19, 0));
        st1.setEndTime(LocalDateTime.of(2026, 8, 15, 21, 30));
        st1.setFormat(ShowtimeFormat.TWO_D);

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(showtimeRepository.findActiveShowtimesPageForReport(any(), any(), any(), any(), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(st1), pageRequest, 1));
        when(seatRepository.sumCapacityByAuditoriumIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{aud.getId(), 100}));
        when(ticketRepository.sumOccupiedCapacityByShowtimeIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{st1.getId(), 2}));

        // Act
        PageResponse<ShowtimeOccupancyResponse> page = reportService.getShowtimeOccupancy(
                from, to, null, null, ReportSortBy.START_TIME, pageRequest);

        // Assert
        assertThat(page.getContent()).hasSize(1);
        ShowtimeOccupancyResponse occ = page.getContent().get(0);
        assertThat(occ.getTotalCapacity()).isEqualTo(100);
        assertThat(occ.getOccupiedSeats()).isEqualTo(2);
        assertThat(occ.getAvailableSeats()).isEqualTo(98);
        assertThat(occ.getOccupancyRate()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("Occupancy: Capacity-weighted aggregate in getDashboardSummary weights by seat volume")
    void getDashboardSummary_CalculatesCapacityWeightedOccupancyRate() {
        // Arrange
        when(paymentRepository.findGrossRevenueBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(refundRepository.findTotalRefundAmountBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(ticketRepository.countTicketKpiBetween(any(), any())).thenReturn(new Object[]{0L, 0L});
        when(bookingRepository.countBookingKpiBetween(any(), any())).thenReturn(new Object[]{0L, 0L, 0L, 0L, 0L});
        when(userRepository.countUserKpiBetween(any(), any())).thenReturn(new Object[]{0L, 0L, 0L, 0L});

        // 2 showtimes, total capacity 110, total occupied 81: (81 / 110) * 100 = 73.64%
        when(showtimeRepository.findOperationsKpiNative(any(), any())).thenReturn(new Object[]{2L, 110L, 81L});

        // Act
        DashboardResponse response = reportService.getDashboardSummary(from, to);

        // Assert
        assertThat(response.getOperations().getTotalShowtimes()).isEqualTo(2L);
        assertThat(response.getOperations().getAverageOccupancyRate()).isEqualByComparingTo("73.64");
    }

    @Test
    @DisplayName("Occupancy: Clamps rate to 100.00% when occupiedCapacity exceeds totalCapacity")
    void getShowtimeOccupancy_ClampsRateTo100_WhenOccupiedExceedsTotalCapacity() {
        Cinema cinema = new Cinema();
        cinema.setId(UUID.randomUUID().toString());
        cinema.setName("Cinema Test");

        Auditorium aud = new Auditorium();
        aud.setId(UUID.randomUUID().toString());
        aud.setName("Auditorium Anomaly");
        aud.setCinema(cinema);

        Movie movie = new Movie();
        movie.setId(UUID.randomUUID().toString());
        movie.setTitle("Anomaly Movie");

        Showtime st = new Showtime();
        st.setId(UUID.randomUUID().toString());
        st.setAuditorium(aud);
        st.setMovie(movie);
        st.setStartTime(LocalDateTime.of(2026, 8, 15, 19, 0));
        st.setEndTime(LocalDateTime.of(2026, 8, 15, 21, 0));
        st.setFormat(ShowtimeFormat.TWO_D);

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(showtimeRepository.findActiveShowtimesPageForReport(any(), any(), any(), any(), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(st), pageRequest, 1));
        when(seatRepository.sumCapacityByAuditoriumIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{aud.getId(), 10}));
        when(ticketRepository.sumOccupiedCapacityByShowtimeIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{st.getId(), 12}));

        PageResponse<ShowtimeOccupancyResponse> page = reportService.getShowtimeOccupancy(
                from, to, null, null, ReportSortBy.START_TIME, pageRequest);

        assertThat(page.getContent()).hasSize(1);
        ShowtimeOccupancyResponse occ = page.getContent().get(0);
        assertThat(occ.getTotalCapacity()).isEqualTo(10);
        assertThat(occ.getOccupiedSeats()).isEqualTo(12);
        assertThat(occ.getAvailableSeats()).isEqualTo(0);
        assertThat(occ.getOccupancyRate()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Occupancy: Sets rate to 0.00 when totalCapacity is 0")
    void getShowtimeOccupancy_ReturnsZeroRate_WhenTotalCapacityIsZero() {
        Cinema cinema = new Cinema();
        cinema.setId(UUID.randomUUID().toString());
        cinema.setName("Cinema Zero");

        Auditorium aud = new Auditorium();
        aud.setId(UUID.randomUUID().toString());
        aud.setName("Auditorium Zero");
        aud.setCinema(cinema);

        Movie movie = new Movie();
        movie.setId(UUID.randomUUID().toString());
        movie.setTitle("Zero Movie");

        Showtime st = new Showtime();
        st.setId(UUID.randomUUID().toString());
        st.setAuditorium(aud);
        st.setMovie(movie);
        st.setStartTime(LocalDateTime.of(2026, 8, 15, 19, 0));
        st.setEndTime(LocalDateTime.of(2026, 8, 15, 21, 0));
        st.setFormat(ShowtimeFormat.TWO_D);

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(showtimeRepository.findActiveShowtimesPageForReport(any(), any(), any(), any(), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(st), pageRequest, 1));
        when(seatRepository.sumCapacityByAuditoriumIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{aud.getId(), 0}));
        when(ticketRepository.sumOccupiedCapacityByShowtimeIds(any()))
                .thenReturn(Collections.emptyList());

        PageResponse<ShowtimeOccupancyResponse> page = reportService.getShowtimeOccupancy(
                from, to, null, null, ReportSortBy.START_TIME, pageRequest);

        assertThat(page.getContent()).hasSize(1);
        ShowtimeOccupancyResponse occ = page.getContent().get(0);
        assertThat(occ.getTotalCapacity()).isEqualTo(0);
        assertThat(occ.getOccupancyRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Occupancy: Sort by OCCUPANCY_RATE queries database directly")
    void getShowtimeOccupancy_SortByOccupancyRate_QueriesNativeDatabase() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(showtimeRepository.countActiveShowtimesForReport(any(), any(), any(), any())).thenReturn(1L);
        when(showtimeRepository.findShowtimeOccupanciesNative(any(), any(), any(), any(), eq(10), eq(0)))
                .thenReturn(List.<Object[]>of(new Object[]{
                        "st-1", "m-1", "Top Movie", "cin-1", "Cinema Alpha", "aud-1", "Aud 1",
                        LocalDateTime.of(2026, 8, 15, 19, 0), LocalDateTime.of(2026, 8, 15, 21, 0),
                        "TWO_D", 100, 85, 15, new BigDecimal("85.00")
                }));

        PageResponse<ShowtimeOccupancyResponse> page = reportService.getShowtimeOccupancy(
                from, to, null, null, ReportSortBy.OCCUPANCY_RATE, pageRequest);

        assertThat(page.getContent()).hasSize(1);
        ShowtimeOccupancyResponse occ = page.getContent().get(0);
        assertThat(occ.getShowtimeId()).isEqualTo("st-1");
        assertThat(occ.getOccupancyRate()).isEqualByComparingTo("85.00");
    }

    @Test
    @DisplayName("Top Occupancy: Queries top showtimes with limit directly")
    void getTopOccupancyShowtimes_QueriesNativeDatabase() {
        when(showtimeRepository.findShowtimeOccupanciesNative(any(), any(), any(), any(), eq(5), eq(0)))
                .thenReturn(List.<Object[]>of(new Object[]{
                        "st-1", "m-1", "Top Movie", "cin-1", "Cinema Alpha", "aud-1", "Aud 1",
                        LocalDateTime.of(2026, 8, 15, 19, 0), LocalDateTime.of(2026, 8, 15, 21, 0),
                        "TWO_D", 100, 95, 5, new BigDecimal("95.00")
                }));

        List<ShowtimeOccupancyResponse> top = reportService.getTopOccupancyShowtimes(from, to, 5);

        assertThat(top).hasSize(1);
        assertThat(top.get(0).getOccupancyRate()).isEqualByComparingTo("95.00");
    }

    @Test
    @DisplayName("Statistics: Booking, User, and Refund statistics return mapped metrics")
    void getStatistics_ReturnsMappedMetrics() {
        when(bookingRepository.findBookingStatisticsBetweenNative(any(), any()))
                .thenReturn(new Object[]{10L, 8L, 1L, 1L, 0L, new BigDecimal("1500000.00")});
        when(userRepository.countUserKpiBetween(any(), any()))
                .thenReturn(new Object[]{100L, 20L, 95L, 5L});
        when(refundRepository.findRefundStatisticsNative(any(), any()))
                .thenReturn(new Object[]{5L, 4L, 1L, 0L, new BigDecimal("200000.00")});

        BookingStatisticsResponse bookingStats = reportService.getBookingStatistics(from, to);
        assertThat(bookingStats.getTotalBookings()).isEqualTo(10L);
        assertThat(bookingStats.getPaidBookings()).isEqualTo(8L);
        assertThat(bookingStats.getTotalBookingAmount()).isEqualByComparingTo("1500000.00");

        UserStatisticsResponse userStats = reportService.getUserStatistics(from, to);
        assertThat(userStats.getTotalUsers()).isEqualTo(100L);
        assertThat(userStats.getNewUsersInPeriod()).isEqualTo(20L);

        RefundStatisticsResponse refundStats = reportService.getRefundStatistics(from, to);
        assertThat(refundStats.getTotalRefunds()).isEqualTo(5L);
        assertThat(refundStats.getSuccessfulRefunds()).isEqualTo(4L);
        assertThat(refundStats.getTotalRefundAmount()).isEqualByComparingTo("200000.00");
    }

    @Test
    @DisplayName("Export CSV & XLSX: Generates non-empty byte arrays with valid signatures")
    void exportReport_GeneratesCsvAndXlsx() {
        when(paymentRepository.findGrossRevenueTrendNative(any(), any(), any())).thenReturn(Collections.emptyList());
        when(refundRepository.findRefundTrendNative(any(), any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findTicketTrendNative(any(), any(), any())).thenReturn(Collections.emptyList());

        // CSV
        byte[] csv = reportService.exportReport(ReportType.REVENUE, ReportFormat.CSV, from, to, ReportGroupBy.DAY, null, null, null, null);
        assertThat(csv).isNotEmpty();
        // UTF-8 BOM check: 0xEF, 0xBB, 0xBF
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);

        // XLSX
        byte[] xlsx = reportService.exportReport(ReportType.REVENUE, ReportFormat.XLSX, from, to, ReportGroupBy.DAY, null, null, null, null);
        assertThat(xlsx).isNotEmpty();
        // Zip file signature check: 0x50, 0x4B (PK)
        assertThat(xlsx[0]).isEqualTo((byte) 0x50);
        assertThat(xlsx[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    @DisplayName("Export Movies, Cinemas, Occupancy: Generates valid CSV and XLSX files")
    void exportReport_OtherTypes_GeneratesValidOutputs() {
        when(paymentRepository.findMovieRevenueReportNative(any(), any())).thenReturn(Collections.emptyList());
        when(refundRepository.findMovieRefundReportNative(any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findMovieTicketReportNative(any(), any())).thenReturn(Collections.emptyList());

        when(paymentRepository.findCinemaRevenueReportNative(any(), any())).thenReturn(Collections.emptyList());
        when(refundRepository.findCinemaRefundReportNative(any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findCinemaTicketReportNative(any(), any())).thenReturn(Collections.emptyList());

        when(showtimeRepository.findActiveShowtimesForReport(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        // MOVIES (CSV and XLSX)
        byte[] moviesCsv = reportService.exportReport(ReportType.MOVIES, ReportFormat.CSV, from, to, null, ReportSortBy.REVENUE, null, null, 10);
        assertThat(moviesCsv).isNotEmpty();
        assertThat(moviesCsv[0]).isEqualTo((byte) 0xEF);

        byte[] moviesXlsx = reportService.exportReport(ReportType.MOVIES, ReportFormat.XLSX, from, to, null, ReportSortBy.REVENUE, null, null, 10);
        assertThat(moviesXlsx).isNotEmpty();
        assertThat(moviesXlsx[0]).isEqualTo((byte) 0x50);

        // CINEMAS (CSV and XLSX)
        byte[] cinemasCsv = reportService.exportReport(ReportType.CINEMAS, ReportFormat.CSV, from, to, null, ReportSortBy.REVENUE, null, null, 10);
        assertThat(cinemasCsv).isNotEmpty();
        assertThat(cinemasCsv[0]).isEqualTo((byte) 0xEF);

        byte[] cinemasXlsx = reportService.exportReport(ReportType.CINEMAS, ReportFormat.XLSX, from, to, null, ReportSortBy.REVENUE, null, null, 10);
        assertThat(cinemasXlsx).isNotEmpty();
        assertThat(cinemasXlsx[0]).isEqualTo((byte) 0x50);

        // OCCUPANCY (CSV and XLSX)
        byte[] occCsv = reportService.exportReport(ReportType.OCCUPANCY, ReportFormat.CSV, from, to, null, null, null, null, null);
        assertThat(occCsv).isNotEmpty();
        assertThat(occCsv[0]).isEqualTo((byte) 0xEF);

        byte[] occXlsx = reportService.exportReport(ReportType.OCCUPANCY, ReportFormat.XLSX, from, to, null, null, null, null, null);
        assertThat(occXlsx).isNotEmpty();
        assertThat(occXlsx[0]).isEqualTo((byte) 0x50);
    }

    @Test
    @DisplayName("Filename: Generates appropriate filename based on ReportType and ReportFormat")
    void getExportFilename_GeneratesCorrectNames() {
        String csvName = reportService.getExportFilename(ReportType.REVENUE, ReportFormat.CSV);
        assertThat(csvName).startsWith("revenue-report-").endsWith(".csv");

        String xlsxName = reportService.getExportFilename(ReportType.MOVIES, ReportFormat.XLSX);
        assertThat(xlsxName).startsWith("movies-report-").endsWith(".xlsx");
    }
}

