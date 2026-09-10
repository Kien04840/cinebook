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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

        when(ticketRepository.countGrossTicketsBetween(any(), any())).thenReturn(10L);
        when(ticketRepository.countRefundedTicketsBetween(any(), any())).thenReturn(1L);

        when(bookingRepository.countByCreatedAtBetween(any(), any())).thenReturn(8L);
        when(bookingRepository.countByBookingStatusAndCreatedAtBetween(eq(BookingStatus.PAID), any(), any())).thenReturn(5L);
        when(bookingRepository.countByBookingStatusAndCreatedAtBetween(eq(BookingStatus.CANCELLED), any(), any())).thenReturn(1L);
        when(bookingRepository.countByBookingStatusAndCreatedAtBetween(eq(BookingStatus.EXPIRED), any(), any())).thenReturn(1L);
        when(bookingRepository.countByBookingStatusAndCreatedAtBetween(eq(BookingStatus.REFUNDED), any(), any())).thenReturn(1L);

        when(userRepository.count()).thenReturn(50L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(10L);
        when(userRepository.countByStatus(eq(UserStatus.ACTIVE))).thenReturn(48L);
        when(userRepository.countByStatus(eq(UserStatus.BLOCKED))).thenReturn(2L);

        when(showtimeRepository.findActiveShowtimesForReport(any(), any(), any(), any())).thenReturn(Collections.emptyList());

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
        Payment p1 = new Payment();
        p1.setAmount(new BigDecimal("200000.00"));
        p1.setPaidAt(LocalDateTime.of(2026, 8, 10, 14, 0));

        Refund r1 = new Refund();
        r1.setAmount(new BigDecimal("50000.00"));
        r1.setProcessedAt(LocalDateTime.of(2026, 8, 10, 16, 0));

        when(paymentRepository.findSuccessfulPaymentsBetween(any(), any())).thenReturn(List.of(p1));
        when(refundRepository.findSuccessfulRefundsBetween(any(), any())).thenReturn(List.of(r1));
        when(ticketRepository.findSoldTicketsBetween(any(), any())).thenReturn(Collections.emptyList());

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
        Movie m1 = new Movie();
        m1.setId(UUID.randomUUID().toString());
        m1.setTitle("Movie A");

        Movie m2 = new Movie();
        m2.setId(UUID.randomUUID().toString());
        m2.setTitle("Movie B");

        Showtime s1 = new Showtime();
        s1.setMovie(m1);
        Booking b1 = new Booking();
        b1.setShowtime(s1);
        Payment p1 = new Payment();
        p1.setBooking(b1);
        p1.setAmount(new BigDecimal("500000.00"));

        Showtime s2 = new Showtime();
        s2.setMovie(m2);
        Booking b2 = new Booking();
        b2.setShowtime(s2);
        Payment p2 = new Payment();
        p2.setBooking(b2);
        p2.setAmount(new BigDecimal("300000.00"));

        when(paymentRepository.findSuccessfulPaymentsBetween(any(), any())).thenReturn(List.of(p1, p2));
        when(refundRepository.findSuccessfulRefundsBetween(any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findSoldTicketsBetween(any(), any())).thenReturn(Collections.emptyList());

        // Act
        List<MovieReportResponse> report = reportService.getMovieReport(from, to, ReportSortBy.REVENUE, 10);

        // Assert
        assertThat(report).hasSize(2);
        assertThat(report.get(0).getMovieTitle()).isEqualTo("Movie A");
        assertThat(report.get(0).getRank()).isEqualTo(1);
        assertThat(report.get(0).getNetRevenue()).isEqualByComparingTo("500000.00");

        assertThat(report.get(1).getMovieTitle()).isEqualTo("Movie B");
        assertThat(report.get(1).getRank()).isEqualTo(2);
        assertThat(report.get(1).getNetRevenue()).isEqualByComparingTo("300000.00");
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


        when(showtimeRepository.findActiveShowtimesForReport(any(), any(), any(), any())).thenReturn(List.of(st1));
        when(seatRepository.sumCapacityByAuditoriumIdAndStatus(eq(aud.getId()), eq(SeatStatus.ACTIVE))).thenReturn(100L);

        SeatType stType = new SeatType();
        stType.setCapacity((short) 1);
        Seat s1 = new Seat();
        s1.setSeatType(stType);
        Seat s2 = new Seat();
        s2.setSeatType(stType);

        Ticket t1 = new Ticket();
        t1.setSeat(s1);
        Ticket t2 = new Ticket();
        t2.setSeat(s2);
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq(st1.getId()), any())).thenReturn(List.of(t1, t2));

        // Act
        PageResponse<ShowtimeOccupancyResponse> page = reportService.getShowtimeOccupancy(
                from, to, null, null, ReportSortBy.START_TIME, PageRequest.of(0, 10));

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
        Cinema cinema = new Cinema();
        cinema.setId(UUID.randomUUID().toString());
        cinema.setName("Cinema Test");

        Auditorium aud1 = new Auditorium();
        aud1.setId(UUID.randomUUID().toString());
        aud1.setName("Auditorium Large");
        aud1.setCinema(cinema);

        Auditorium aud2 = new Auditorium();
        aud2.setId(UUID.randomUUID().toString());
        aud2.setName("Auditorium Small");
        aud2.setCinema(cinema);

        Movie movie = new Movie();
        movie.setId(UUID.randomUUID().toString());
        movie.setTitle("Movie 1");

        Showtime st1 = new Showtime();
        st1.setId(UUID.randomUUID().toString());
        st1.setAuditorium(aud1);
        st1.setMovie(movie);
        st1.setStartTime(LocalDateTime.of(2026, 8, 15, 18, 0));
        st1.setEndTime(LocalDateTime.of(2026, 8, 15, 20, 0));
        st1.setFormat(ShowtimeFormat.TWO_D);

        Showtime st2 = new Showtime();
        st2.setId(UUID.randomUUID().toString());
        st2.setAuditorium(aud2);
        st2.setMovie(movie);
        st2.setStartTime(LocalDateTime.of(2026, 8, 15, 20, 30));
        st2.setEndTime(LocalDateTime.of(2026, 8, 15, 22, 30));
        st2.setFormat(ShowtimeFormat.TWO_D);

        when(showtimeRepository.findActiveShowtimesForReport(any(), any(), any(), any())).thenReturn(List.of(st1, st2));
        when(seatRepository.sumCapacityByAuditoriumIdAndStatus(eq(aud1.getId()), eq(SeatStatus.ACTIVE))).thenReturn(100L);
        when(seatRepository.sumCapacityByAuditoriumIdAndStatus(eq(aud2.getId()), eq(SeatStatus.ACTIVE))).thenReturn(10L);

        // aud1 has 80 occupied seats (80%)
        SeatType stType = new SeatType();
        stType.setCapacity((short) 1);
        List<Ticket> tickets1 = Collections.nCopies(80, new Ticket()).stream().map(t -> {
            Ticket ticket = new Ticket();
            Seat seat = new Seat();
            seat.setSeatType(stType);
            ticket.setSeat(seat);
            return ticket;
        }).toList();
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq(st1.getId()), any())).thenReturn(tickets1);

        // aud2 has 1 occupied seat (10%)
        Ticket ticket2 = new Ticket();
        Seat seat2 = new Seat();
        seat2.setSeatType(stType);
        ticket2.setSeat(seat2);
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq(st2.getId()), any())).thenReturn(List.of(ticket2));

        // Act
        DashboardResponse response = reportService.getDashboardSummary(from, to);

        // Assert:
        // Arithmetic average would be (80 + 10) / 2 = 45.00%
        // Capacity-weighted: (80 + 1) / (100 + 10) * 100 = 81 / 110 * 100 = 73.64%
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

        when(showtimeRepository.findActiveShowtimesForReport(any(), any(), any(), any())).thenReturn(List.of(st));
        when(seatRepository.sumCapacityByAuditoriumIdAndStatus(eq(aud.getId()), eq(SeatStatus.ACTIVE))).thenReturn(10L);

        // 12 tickets for capacity of 10
        SeatType stType = new SeatType();
        stType.setCapacity((short) 1);
        List<Ticket> tickets = Collections.nCopies(12, new Ticket()).stream().map(t -> {
            Ticket ticket = new Ticket();
            Seat seat = new Seat();
            seat.setSeatType(stType);
            ticket.setSeat(seat);
            return ticket;
        }).toList();
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq(st.getId()), any())).thenReturn(tickets);

        PageResponse<ShowtimeOccupancyResponse> page = reportService.getShowtimeOccupancy(
                from, to, null, null, ReportSortBy.START_TIME, PageRequest.of(0, 10));

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

        when(showtimeRepository.findActiveShowtimesForReport(any(), any(), any(), any())).thenReturn(List.of(st));
        when(seatRepository.sumCapacityByAuditoriumIdAndStatus(eq(aud.getId()), eq(SeatStatus.ACTIVE))).thenReturn(0L);
        when(ticketRepository.findTicketsByShowtimeIdAndStatuses(eq(st.getId()), any())).thenReturn(Collections.emptyList());

        PageResponse<ShowtimeOccupancyResponse> page = reportService.getShowtimeOccupancy(
                from, to, null, null, ReportSortBy.START_TIME, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        ShowtimeOccupancyResponse occ = page.getContent().get(0);
        assertThat(occ.getTotalCapacity()).isEqualTo(0);
        assertThat(occ.getOccupancyRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Export CSV & XLSX: Generates non-empty byte arrays with valid signatures")
    void exportReport_GeneratesCsvAndXlsx() {
        when(paymentRepository.findSuccessfulPaymentsBetween(any(), any())).thenReturn(Collections.emptyList());
        when(refundRepository.findSuccessfulRefundsBetween(any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findSoldTicketsBetween(any(), any())).thenReturn(Collections.emptyList());

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
        when(paymentRepository.findSuccessfulPaymentsBetween(any(), any())).thenReturn(Collections.emptyList());
        when(refundRepository.findSuccessfulRefundsBetween(any(), any())).thenReturn(Collections.emptyList());
        when(ticketRepository.findSoldTicketsBetween(any(), any())).thenReturn(Collections.emptyList());
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
