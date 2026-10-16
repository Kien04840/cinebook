package com.cinebook.service.impl;

import com.cinebook.dto.response.*;
import com.cinebook.entity.*;
import com.cinebook.enums.*;
import com.cinebook.exception.BadRequestException;
import com.cinebook.repository.*;
import com.cinebook.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateRange normalizeDateRange(LocalDateTime from, LocalDateTime to) {
        LocalDateTime effectiveFrom = from;
        LocalDateTime effectiveTo = to;

        if (effectiveFrom == null) {
            effectiveFrom = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        }
        if (effectiveTo == null) {
            effectiveTo = LocalDate.now().atTime(23, 59, 59, 999999999);
        }

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("Ngày bắt đầu không được lớn hơn ngày kết thúc.");
        }

        return new DateRange(effectiveFrom, effectiveTo);
    }

    @Override
    public DashboardResponse getDashboardSummary(LocalDateTime from, LocalDateTime to) {
        DateRange range = normalizeDateRange(from, to);

        // 1. Financial
        BigDecimal grossRevenue = paymentRepository.findGrossRevenueBetween(range.from, range.to);
        if (grossRevenue == null) grossRevenue = BigDecimal.ZERO;
        grossRevenue = grossRevenue.setScale(2, RoundingMode.HALF_UP);

        BigDecimal refundAmount = refundRepository.findTotalRefundAmountBetween(range.from, range.to);
        if (refundAmount == null) refundAmount = BigDecimal.ZERO;
        refundAmount = refundAmount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal netRevenue = grossRevenue.subtract(refundAmount).setScale(2, RoundingMode.HALF_UP);

        FinancialKpiResponse financial = FinancialKpiResponse.builder()
                .grossRevenue(grossRevenue)
                .refundAmount(refundAmount)
                .netRevenue(netRevenue)
                .build();

        // 2. Tickets
        Object[] ticketKpi = extractSingleRow(ticketRepository.countTicketKpiBetween(range.from, range.to));
        long grossTickets = ticketKpi != null && ticketKpi.length > 0 ? toLong(ticketKpi[0]) : 0L;
        long refundedTickets = ticketKpi != null && ticketKpi.length > 1 ? toLong(ticketKpi[1]) : 0L;
        long netTickets = Math.max(0, grossTickets - refundedTickets);

        TicketKpiResponse tickets = TicketKpiResponse.builder()
                .grossTicketsSold(grossTickets)
                .refundedTickets(refundedTickets)
                .netTicketsSold(netTickets)
                .build();

        // 3. Bookings
        Object[] bookingKpi = extractSingleRow(bookingRepository.countBookingKpiBetween(range.from, range.to));
        long totalBookings = bookingKpi != null && bookingKpi.length > 0 ? toLong(bookingKpi[0]) : 0L;
        long paidBookings = bookingKpi != null && bookingKpi.length > 1 ? toLong(bookingKpi[1]) : 0L;
        long cancelledBookings = bookingKpi != null && bookingKpi.length > 2 ? toLong(bookingKpi[2]) : 0L;
        long expiredBookings = bookingKpi != null && bookingKpi.length > 3 ? toLong(bookingKpi[3]) : 0L;
        long refundedBookings = bookingKpi != null && bookingKpi.length > 4 ? toLong(bookingKpi[4]) : 0L;

        BookingKpiResponse bookings = BookingKpiResponse.builder()
                .totalBookings(totalBookings)
                .paidBookings(paidBookings)
                .cancelledBookings(cancelledBookings)
                .expiredBookings(expiredBookings)
                .refundedBookings(refundedBookings)
                .build();

        // 4. Users
        Object[] userKpi = extractSingleRow(userRepository.countUserKpiBetween(range.from, range.to));
        long totalUsers = userKpi != null && userKpi.length > 0 ? toLong(userKpi[0]) : 0L;
        long newUsers = userKpi != null && userKpi.length > 1 ? toLong(userKpi[1]) : 0L;
        long activeUsers = userKpi != null && userKpi.length > 2 ? toLong(userKpi[2]) : 0L;
        long blockedUsers = userKpi != null && userKpi.length > 3 ? toLong(userKpi[3]) : 0L;

        UserKpiResponse users = UserKpiResponse.builder()
                .totalUsers(totalUsers)
                .newUsersInPeriod(newUsers)
                .activeUsers(activeUsers)
                .blockedUsers(blockedUsers)
                .build();

        // 5. Operations / Occupancy
        Object[] opsKpi = extractSingleRow(showtimeRepository.findOperationsKpiNative(range.from, range.to));
        long totalShowtimes = opsKpi != null && opsKpi.length > 0 ? toLong(opsKpi[0]) : 0L;
        long totalCapacity = opsKpi != null && opsKpi.length > 1 ? toLong(opsKpi[1]) : 0L;
        long totalOccupiedSeats = opsKpi != null && opsKpi.length > 2 ? toLong(opsKpi[2]) : 0L;

        BigDecimal avgOccupancy = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (totalCapacity > 0) {
            double rawRate = (double) totalOccupiedSeats / totalCapacity * 100.0;
            double guardedRate = Math.min(100.0, Math.max(0.0, rawRate));
            avgOccupancy = BigDecimal.valueOf(guardedRate).setScale(2, RoundingMode.HALF_UP);
        }

        OperationKpiResponse operations = OperationKpiResponse.builder()
                .totalShowtimes(totalShowtimes)
                .averageOccupancyRate(avgOccupancy)
                .build();

        return DashboardResponse.builder()
                .from(range.from)
                .to(range.to)
                .financial(financial)
                .tickets(tickets)
                .bookings(bookings)
                .users(users)
                .operations(operations)
                .build();
    }

    @Override
    public List<RevenueTrendResponse> getRevenueTrend(LocalDateTime from, LocalDateTime to, ReportGroupBy groupBy) {
        DateRange range = normalizeDateRange(from, to);
        ReportGroupBy effectiveGroupBy = groupBy != null ? groupBy : ReportGroupBy.DAY;
        String sqlDateFormat = effectiveGroupBy == ReportGroupBy.MONTH ? "%Y-%m" : "%Y-%m-%d";

        Map<String, TrendAccumulator> trendMap = new TreeMap<>();

        List<Object[]> revenueRows = paymentRepository.findGrossRevenueTrendNative(range.from, range.to, sqlDateFormat);
        if (revenueRows != null) {
            for (Object[] row : revenueRows) {
                String period = (String) row[0];
                BigDecimal gross = toBigDecimal(row[1]);
                TrendAccumulator acc = trendMap.computeIfAbsent(period, TrendAccumulator::new);
                acc.grossRevenue = gross;
            }
        }

        List<Object[]> refundRows = refundRepository.findRefundTrendNative(range.from, range.to, sqlDateFormat);
        if (refundRows != null) {
            for (Object[] row : refundRows) {
                String period = (String) row[0];
                BigDecimal refund = toBigDecimal(row[1]);
                TrendAccumulator acc = trendMap.computeIfAbsent(period, TrendAccumulator::new);
                acc.refundAmount = refund;
            }
        }

        List<Object[]> ticketRows = ticketRepository.findTicketTrendNative(range.from, range.to, sqlDateFormat);
        if (ticketRows != null) {
            for (Object[] row : ticketRows) {
                String period = (String) row[0];
                long count = toLong(row[1]);
                TrendAccumulator acc = trendMap.computeIfAbsent(period, TrendAccumulator::new);
                acc.ticketCount = count;
            }
        }

        return trendMap.values().stream()
                .map(acc -> RevenueTrendResponse.builder()
                        .period(acc.period)
                        .grossRevenue(acc.grossRevenue.setScale(2, RoundingMode.HALF_UP))
                        .refundAmount(acc.refundAmount.setScale(2, RoundingMode.HALF_UP))
                        .netRevenue(acc.grossRevenue.subtract(acc.refundAmount).setScale(2, RoundingMode.HALF_UP))
                        .ticketCount(acc.ticketCount)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<MovieReportResponse> getMovieReport(LocalDateTime from, LocalDateTime to, ReportSortBy sortBy, Integer limit) {
        DateRange range = normalizeDateRange(from, to);
        ReportSortBy effectiveSort = (sortBy == ReportSortBy.TICKETS) ? ReportSortBy.TICKETS : ReportSortBy.REVENUE;

        Map<String, MovieAccumulator> movieMap = new HashMap<>();

        List<Object[]> paymentRows = paymentRepository.findMovieRevenueReportNative(range.from, range.to);
        if (paymentRows != null) {
            for (Object[] row : paymentRows) {
                String movieId = (String) row[0];
                String title = (String) row[1];
                String posterUrl = (String) row[2];
                BigDecimal gross = toBigDecimal(row[3]);
                long totalBookings = toLong(row[4]);

                MovieAccumulator acc = movieMap.computeIfAbsent(movieId, k -> new MovieAccumulator(movieId, title, posterUrl));
                acc.grossRevenue = gross;
                acc.totalBookings = totalBookings;
            }
        }

        List<Object[]> refundRows = refundRepository.findMovieRefundReportNative(range.from, range.to);
        if (refundRows != null) {
            for (Object[] row : refundRows) {
                String movieId = (String) row[0];
                BigDecimal refund = toBigDecimal(row[1]);

                MovieAccumulator acc = movieMap.computeIfAbsent(movieId, k -> new MovieAccumulator(movieId, "", null));
                acc.refundAmount = refund;
            }
        }

        List<Object[]> ticketRows = ticketRepository.findMovieTicketReportNative(range.from, range.to);
        if (ticketRows != null) {
            for (Object[] row : ticketRows) {
                String movieId = (String) row[0];
                long grossTickets = toLong(row[1]);
                long refundedTickets = toLong(row[2]);

                MovieAccumulator acc = movieMap.computeIfAbsent(movieId, k -> new MovieAccumulator(movieId, "", null));
                acc.grossTicketsSold = grossTickets;
                acc.refundedTickets = refundedTickets;
            }
        }

        List<MovieAccumulator> list = new ArrayList<>(movieMap.values());

        if (effectiveSort == ReportSortBy.TICKETS) {
            list.sort((a, b) -> {
                long netA = a.grossTicketsSold - a.refundedTickets;
                long netB = b.grossTicketsSold - b.refundedTickets;
                int cmp = Long.compare(netB, netA);
                if (cmp != 0) return cmp;
                return a.title.compareToIgnoreCase(b.title);
            });
        } else {
            list.sort((a, b) -> {
                BigDecimal netA = a.grossRevenue.subtract(a.refundAmount);
                BigDecimal netB = b.grossRevenue.subtract(b.refundAmount);
                int cmp = netB.compareTo(netA);
                if (cmp != 0) return cmp;
                return a.title.compareToIgnoreCase(b.title);
            });
        }

        if (limit != null && limit > 0 && limit < list.size()) {
            list = list.subList(0, limit);
        }

        List<MovieReportResponse> results = new ArrayList<>(list.size());
        int rank = 1;
        for (MovieAccumulator acc : list) {
            BigDecimal netRev = acc.grossRevenue.subtract(acc.refundAmount).setScale(2, RoundingMode.HALF_UP);
            long netTix = Math.max(0, acc.grossTicketsSold - acc.refundedTickets);

            results.add(MovieReportResponse.builder()
                    .rank(rank++)
                    .movieId(acc.movieId)
                    .movieTitle(acc.title)
                    .posterUrl(acc.posterUrl)
                    .grossRevenue(acc.grossRevenue.setScale(2, RoundingMode.HALF_UP))
                    .refundAmount(acc.refundAmount.setScale(2, RoundingMode.HALF_UP))
                    .netRevenue(netRev)
                    .totalRevenue(netRev)
                    .grossTicketsSold(acc.grossTicketsSold)
                    .refundedTickets(acc.refundedTickets)
                    .netTicketsSold(netTix)
                    .ticketsSold(netTix)
                    .totalBookings(acc.totalBookings)
                    .build());
        }

        return results;
    }

    @Override
    public List<CinemaReportResponse> getCinemaReport(LocalDateTime from, LocalDateTime to, ReportSortBy sortBy, Integer limit) {
        DateRange range = normalizeDateRange(from, to);
        ReportSortBy effectiveSort = (sortBy == ReportSortBy.TICKETS) ? ReportSortBy.TICKETS : ReportSortBy.REVENUE;

        Map<String, CinemaAccumulator> cinemaMap = new HashMap<>();

        List<Object[]> paymentRows = paymentRepository.findCinemaRevenueReportNative(range.from, range.to);
        if (paymentRows != null) {
            for (Object[] row : paymentRows) {
                String cinemaId = (String) row[0];
                String name = (String) row[1];
                String city = (String) row[2];
                BigDecimal gross = toBigDecimal(row[3]);
                long totalShowtimes = toLong(row[4]);

                CinemaAccumulator acc = cinemaMap.computeIfAbsent(cinemaId, k -> new CinemaAccumulator(cinemaId, name, city));
                acc.grossRevenue = gross;
                acc.totalShowtimes = totalShowtimes;
            }
        }

        List<Object[]> refundRows = refundRepository.findCinemaRefundReportNative(range.from, range.to);
        if (refundRows != null) {
            for (Object[] row : refundRows) {
                String cinemaId = (String) row[0];
                BigDecimal refund = toBigDecimal(row[1]);

                CinemaAccumulator acc = cinemaMap.computeIfAbsent(cinemaId, k -> new CinemaAccumulator(cinemaId, "", ""));
                acc.refundAmount = refund;
            }
        }

        List<Object[]> ticketRows = ticketRepository.findCinemaTicketReportNative(range.from, range.to);
        if (ticketRows != null) {
            for (Object[] row : ticketRows) {
                String cinemaId = (String) row[0];
                long grossTickets = toLong(row[1]);
                long refundedTickets = toLong(row[2]);

                CinemaAccumulator acc = cinemaMap.computeIfAbsent(cinemaId, k -> new CinemaAccumulator(cinemaId, "", ""));
                acc.grossTicketsSold = grossTickets;
                acc.refundedTickets = refundedTickets;
            }
        }

        List<CinemaAccumulator> list = new ArrayList<>(cinemaMap.values());

        if (effectiveSort == ReportSortBy.TICKETS) {
            list.sort((a, b) -> {
                long netA = a.grossTicketsSold - a.refundedTickets;
                long netB = b.grossTicketsSold - b.refundedTickets;
                int cmp = Long.compare(netB, netA);
                if (cmp != 0) return cmp;
                return a.name.compareToIgnoreCase(b.name);
            });
        } else {
            list.sort((a, b) -> {
                BigDecimal netA = a.grossRevenue.subtract(a.refundAmount);
                BigDecimal netB = b.grossRevenue.subtract(b.refundAmount);
                int cmp = netB.compareTo(netA);
                if (cmp != 0) return cmp;
                return a.name.compareToIgnoreCase(b.name);
            });
        }

        if (limit != null && limit > 0 && limit < list.size()) {
            list = list.subList(0, limit);
        }

        List<CinemaReportResponse> results = new ArrayList<>(list.size());
        int rank = 1;
        for (CinemaAccumulator acc : list) {
            BigDecimal netRev = acc.grossRevenue.subtract(acc.refundAmount).setScale(2, RoundingMode.HALF_UP);
            long netTix = Math.max(0, acc.grossTicketsSold - acc.refundedTickets);

            results.add(CinemaReportResponse.builder()
                    .rank(rank++)
                    .cinemaId(acc.cinemaId)
                    .cinemaName(acc.name)
                    .city(acc.city)
                    .grossRevenue(acc.grossRevenue.setScale(2, RoundingMode.HALF_UP))
                    .refundAmount(acc.refundAmount.setScale(2, RoundingMode.HALF_UP))
                    .netRevenue(netRev)
                    .totalRevenue(netRev)
                    .grossTicketsSold(acc.grossTicketsSold)
                    .refundedTickets(acc.refundedTickets)
                    .netTicketsSold(netTix)
                    .ticketsSold(netTix)
                    .totalShowtimes(acc.totalShowtimes)
                    .build());
        }

        return results;
    }

    private List<ShowtimeOccupancyResponse> calculateOccupancies(LocalDateTime from, LocalDateTime to, String cinemaId, String movieId) {
        DateRange range = normalizeDateRange(from, to);
        List<Showtime> showtimes = showtimeRepository.findActiveShowtimesForReport(range.from, range.to, cinemaId, movieId);
        if (showtimes == null || showtimes.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> audIds = showtimes.stream()
                .map(s -> s.getAuditorium().getId())
                .collect(Collectors.toSet());
        Set<String> showtimeIds = showtimes.stream()
                .map(Showtime::getId)
                .collect(Collectors.toSet());

        Map<String, Integer> auditoriumCapacityMap = fetchAuditoriumCapacities(audIds);
        Map<String, Integer> showtimeOccupiedMap = fetchOccupiedCapacities(showtimeIds);

        List<ShowtimeOccupancyResponse> results = new ArrayList<>(showtimes.size());

        for (Showtime s : showtimes) {
            String audId = s.getAuditorium().getId();
            int totalCapacity = auditoriumCapacityMap.getOrDefault(audId, 0);
            int occupiedCapacity = showtimeOccupiedMap.getOrDefault(s.getId(), 0);
            int availableSeats = Math.max(0, totalCapacity - occupiedCapacity);

            if (occupiedCapacity > totalCapacity) {
                log.warn("[OCCUPANCY ANOMALY] Showtime ID {} has occupiedCapacity ({}) exceeding totalCapacity ({})",
                        s.getId(), occupiedCapacity, totalCapacity);
            }

            BigDecimal occupancyRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (totalCapacity > 0) {
                double rawRate = (double) occupiedCapacity / totalCapacity * 100.0;
                double guardedRate = Math.min(100.0, Math.max(0.0, rawRate));
                occupancyRate = BigDecimal.valueOf(guardedRate).setScale(2, RoundingMode.HALF_UP);
            }

            results.add(ShowtimeOccupancyResponse.builder()
                    .showtimeId(s.getId())
                    .movieId(s.getMovie().getId())
                    .movieTitle(s.getMovie().getTitle())
                    .cinemaId(s.getAuditorium().getCinema().getId())
                    .cinemaName(s.getAuditorium().getCinema().getName())
                    .auditoriumId(s.getAuditorium().getId())
                    .auditoriumName(s.getAuditorium().getName())
                    .startTime(s.getStartTime())
                    .endTime(s.getEndTime())
                    .format(s.getFormat())
                    .totalCapacity(totalCapacity)
                    .occupiedSeats(occupiedCapacity)
                    .availableSeats(availableSeats)
                    .occupancyRate(occupancyRate)
                    .build());
        }

        return results;
    }

    @Override
    public PageResponse<ShowtimeOccupancyResponse> getShowtimeOccupancy(
            LocalDateTime from,
            LocalDateTime to,
            String cinemaId,
            String movieId,
            ReportSortBy sortBy,
            Pageable pageable
    ) {
        DateRange range = normalizeDateRange(from, to);
        int pageSize = pageable != null && pageable.getPageSize() > 0 ? pageable.getPageSize() : 20;
        int pageNumber = pageable != null && pageable.getPageNumber() >= 0 ? pageable.getPageNumber() : 0;
        int offset = pageNumber * pageSize;

        if (sortBy == ReportSortBy.OCCUPANCY_RATE) {
            long total = showtimeRepository.countActiveShowtimesForReport(range.from, range.to, cinemaId, movieId);
            if (total == 0) {
                return PageResponse.of(new PageImpl<>(Collections.emptyList(), pageable != null ? pageable : Pageable.unpaged(), 0));
            }
            List<Object[]> rows = showtimeRepository.findShowtimeOccupanciesNative(
                    range.from, range.to, cinemaId, movieId, pageSize, offset);
            List<ShowtimeOccupancyResponse> content = new ArrayList<>();
            if (rows != null) {
                for (Object[] row : rows) {
                    content.add(mapShowtimeOccupancyRow(row));
                }
            }
            return PageResponse.of(new PageImpl<>(content, pageable != null ? pageable : Pageable.unpaged(), total));
        }

        org.springframework.data.domain.Page<Showtime> page = showtimeRepository.findActiveShowtimesPageForReport(
                range.from, range.to, cinemaId, movieId, pageable);
        if (page == null || page.isEmpty()) {
            return PageResponse.of(new PageImpl<>(Collections.emptyList(), pageable != null ? pageable : Pageable.unpaged(), page != null ? page.getTotalElements() : 0));
        }

        List<Showtime> showtimes = page.getContent();
        Set<String> audIds = showtimes.stream().map(s -> s.getAuditorium().getId()).collect(Collectors.toSet());
        Set<String> showtimeIds = showtimes.stream().map(Showtime::getId).collect(Collectors.toSet());

        Map<String, Integer> audCapMap = fetchAuditoriumCapacities(audIds);
        Map<String, Integer> showtimeOccMap = fetchOccupiedCapacities(showtimeIds);

        List<ShowtimeOccupancyResponse> content = new ArrayList<>(showtimes.size());
        for (Showtime s : showtimes) {
            String audId = s.getAuditorium().getId();
            int totalCapacity = audCapMap.getOrDefault(audId, 0);
            int occupiedCapacity = showtimeOccMap.getOrDefault(s.getId(), 0);
            int availableSeats = Math.max(0, totalCapacity - occupiedCapacity);

            if (occupiedCapacity > totalCapacity) {
                log.warn("[OCCUPANCY ANOMALY] Showtime ID {} has occupiedCapacity ({}) exceeding totalCapacity ({})",
                        s.getId(), occupiedCapacity, totalCapacity);
            }

            BigDecimal occupancyRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (totalCapacity > 0) {
                double rawRate = (double) occupiedCapacity / totalCapacity * 100.0;
                double guardedRate = Math.min(100.0, Math.max(0.0, rawRate));
                occupancyRate = BigDecimal.valueOf(guardedRate).setScale(2, RoundingMode.HALF_UP);
            }

            content.add(ShowtimeOccupancyResponse.builder()
                    .showtimeId(s.getId())
                    .movieId(s.getMovie().getId())
                    .movieTitle(s.getMovie().getTitle())
                    .cinemaId(s.getAuditorium().getCinema().getId())
                    .cinemaName(s.getAuditorium().getCinema().getName())
                    .auditoriumId(s.getAuditorium().getId())
                    .auditoriumName(s.getAuditorium().getName())
                    .startTime(s.getStartTime())
                    .endTime(s.getEndTime())
                    .format(s.getFormat())
                    .totalCapacity(totalCapacity)
                    .occupiedSeats(occupiedCapacity)
                    .availableSeats(availableSeats)
                    .occupancyRate(occupancyRate)
                    .build());
        }

        return PageResponse.of(new PageImpl<>(content, pageable != null ? pageable : Pageable.unpaged(), page.getTotalElements()));
    }

    @Override
    public List<ShowtimeOccupancyResponse> getTopOccupancyShowtimes(LocalDateTime from, LocalDateTime to, Integer limit) {
        DateRange range = normalizeDateRange(from, to);
        int maxLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 10;

        List<Object[]> rows = showtimeRepository.findShowtimeOccupanciesNative(
                range.from, range.to, null, null, maxLimit, 0);
        List<ShowtimeOccupancyResponse> results = new ArrayList<>();
        if (rows != null) {
            for (Object[] row : rows) {
                results.add(mapShowtimeOccupancyRow(row));
            }
        }
        return results;
    }

    @Override
    public BookingStatisticsResponse getBookingStatistics(LocalDateTime from, LocalDateTime to) {
        DateRange range = normalizeDateRange(from, to);

        Object[] stats = extractSingleRow(bookingRepository.findBookingStatisticsBetweenNative(range.from, range.to));
        long total = stats != null && stats.length > 0 ? toLong(stats[0]) : 0L;
        long paid = stats != null && stats.length > 1 ? toLong(stats[1]) : 0L;
        long cancelled = stats != null && stats.length > 2 ? toLong(stats[2]) : 0L;
        long expired = stats != null && stats.length > 3 ? toLong(stats[3]) : 0L;
        long refunded = stats != null && stats.length > 4 ? toLong(stats[4]) : 0L;
        BigDecimal amount = stats != null && stats.length > 5 ? toBigDecimal(stats[5]) : BigDecimal.ZERO;

        return BookingStatisticsResponse.builder()
                .from(range.from)
                .to(range.to)
                .totalBookings(total)
                .paidBookings(paid)
                .cancelledBookings(cancelled)
                .expiredBookings(expired)
                .refundedBookings(refunded)
                .totalBookingAmount(amount.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    @Override
    public UserStatisticsResponse getUserStatistics(LocalDateTime from, LocalDateTime to) {
        DateRange range = normalizeDateRange(from, to);

        Object[] stats = extractSingleRow(userRepository.countUserKpiBetween(range.from, range.to));
        long total = stats != null && stats.length > 0 ? toLong(stats[0]) : 0L;
        long newUsers = stats != null && stats.length > 1 ? toLong(stats[1]) : 0L;
        long active = stats != null && stats.length > 2 ? toLong(stats[2]) : 0L;
        long blocked = stats != null && stats.length > 3 ? toLong(stats[3]) : 0L;

        return UserStatisticsResponse.builder()
                .from(range.from)
                .to(range.to)
                .totalUsers(total)
                .newUsersInPeriod(newUsers)
                .activeUsers(active)
                .blockedUsers(blocked)
                .build();
    }

    @Override
    public RefundStatisticsResponse getRefundStatistics(LocalDateTime from, LocalDateTime to) {
        DateRange range = normalizeDateRange(from, to);

        Object[] stats = extractSingleRow(refundRepository.findRefundStatisticsNative(range.from, range.to));
        long total = stats != null && stats.length > 0 ? toLong(stats[0]) : 0L;
        long successful = stats != null && stats.length > 1 ? toLong(stats[1]) : 0L;
        long failed = stats != null && stats.length > 2 ? toLong(stats[2]) : 0L;
        long pending = stats != null && stats.length > 3 ? toLong(stats[3]) : 0L;
        BigDecimal amount = stats != null && stats.length > 4 ? toBigDecimal(stats[4]) : BigDecimal.ZERO;

        return RefundStatisticsResponse.builder()
                .from(range.from)
                .to(range.to)
                .totalRefunds(total)
                .successfulRefunds(successful)
                .failedRefunds(failed)
                .pendingRefunds(pending)
                .totalRefundAmount(amount.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    @Override
    public String getExportFilename(ReportType reportType, ReportFormat format) {
        String base = (reportType != null ? reportType.name().toLowerCase() : "report");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String ext = (format == ReportFormat.XLSX) ? "xlsx" : "csv";
        return String.format("%s-report-%s.%s", base, timestamp, ext);
    }

    @Override
    public byte[] exportReport(
            ReportType reportType,
            ReportFormat format,
            LocalDateTime from,
            LocalDateTime to,
            ReportGroupBy groupBy,
            ReportSortBy sortBy,
            String cinemaId,
            String movieId,
            Integer limit
    ) {
        DateRange range = normalizeDateRange(from, to);
        ReportType type = reportType != null ? reportType : ReportType.REVENUE;
        ReportFormat fmt = format != null ? format : ReportFormat.CSV;

        if (fmt == ReportFormat.XLSX) {
            return exportXlsx(type, range.from, range.to, groupBy, sortBy, cinemaId, movieId, limit);
        } else {
            return exportCsv(type, range.from, range.to, groupBy, sortBy, cinemaId, movieId, limit);
        }
    }

    private byte[] exportCsv(
            ReportType reportType,
            LocalDateTime from,
            LocalDateTime to,
            ReportGroupBy groupBy,
            ReportSortBy sortBy,
            String cinemaId,
            String movieId,
            Integer limit
    ) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // UTF-8 BOM
        pw.print('\uFEFF');

        switch (reportType) {
            case REVENUE -> {
                pw.println("Thời gian,Doanh thu gộp (VND),Số tiền hoàn (VND),Doanh thu thuần (VND),Số lượng vé");
                List<RevenueTrendResponse> list = getRevenueTrend(from, to, groupBy);
                for (RevenueTrendResponse r : list) {
                    pw.printf("\"%s\",%s,%s,%s,%d%n",
                            r.getPeriod(), r.getGrossRevenue(), r.getRefundAmount(), r.getNetRevenue(), r.getTicketCount());
                }
            }
            case MOVIES -> {
                pw.println("Xếp hạng,Mã phim,Tên phim,Doanh thu gộp (VND),Số tiền hoàn (VND),Doanh thu thuần (VND),Vé bán ra,Vé hoàn,Vé thuần");
                List<MovieReportResponse> list = getMovieReport(from, to, sortBy, limit);
                for (MovieReportResponse r : list) {
                    pw.printf("%d,\"%s\",\"%s\",%s,%s,%s,%d,%d,%d%n",
                            r.getRank(), r.getMovieId(), escapeCsv(r.getMovieTitle()),
                            r.getGrossRevenue(), r.getRefundAmount(), r.getNetRevenue(),
                            r.getGrossTicketsSold(), r.getRefundedTickets(), r.getNetTicketsSold());
                }
            }
            case CINEMAS -> {
                pw.println("Xếp hạng,Mã rạp,Tên rạp,Thành phố,Doanh thu gộp (VND),Số tiền hoàn (VND),Doanh thu thuần (VND),Vé bán ra,Vé hoàn,Vé thuần");
                List<CinemaReportResponse> list = getCinemaReport(from, to, sortBy, limit);
                for (CinemaReportResponse r : list) {
                    pw.printf("%d,\"%s\",\"%s\",\"%s\",%s,%s,%s,%d,%d,%d%n",
                            r.getRank(), r.getCinemaId(), escapeCsv(r.getCinemaName()), escapeCsv(r.getCity()),
                            r.getGrossRevenue(), r.getRefundAmount(), r.getNetRevenue(),
                            r.getGrossTicketsSold(), r.getRefundedTickets(), r.getNetTicketsSold());
                }
            }
            case OCCUPANCY -> {
                pw.println("Mã suất chiếu,Phim,Rạp,Phòng chiếu,Bắt đầu,Kết thúc,Định dạng,Tổng ghế,Ghế đã đặt,Ghế trống,Tỷ lệ lấp đầy (%)");
                List<ShowtimeOccupancyResponse> list = calculateOccupancies(from, to, cinemaId, movieId);
                for (ShowtimeOccupancyResponse r : list) {
                    pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%d,%s%n",
                            r.getShowtimeId(), escapeCsv(r.getMovieTitle()), escapeCsv(r.getCinemaName()),
                            escapeCsv(r.getAuditoriumName()), r.getStartTime().format(DATETIME_FORMATTER),
                            r.getEndTime().format(DATETIME_FORMATTER), r.getFormat(),
                            r.getTotalCapacity(), r.getOccupiedSeats(), r.getAvailableSeats(), r.getOccupancyRate());
                }
            }
        }

        pw.flush();
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportXlsx(
            ReportType reportType,
            LocalDateTime from,
            LocalDateTime to,
            ReportGroupBy groupBy,
            ReportSortBy sortBy,
            String cinemaId,
            String movieId,
            Integer limit
    ) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(reportType.name());

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 11);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            // Data Cell Style
            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            currencyStyle.setDataFormat(dataFormat.getFormat("#,##0"));

            int rowIdx = 0;
            Row headerRow = sheet.createRow(rowIdx++);

            switch (reportType) {
                case REVENUE -> {
                    String[] headers = {"Thời gian", "Doanh thu gộp (VND)", "Số tiền hoàn (VND)", "Doanh thu thuần (VND)", "Số lượng vé"};
                    for (int i = 0; i < headers.length; i++) {
                        Cell c = headerRow.createCell(i);
                        c.setCellValue(headers[i]);
                        c.setCellStyle(headerStyle);
                    }
                    List<RevenueTrendResponse> list = getRevenueTrend(from, to, groupBy);
                    for (RevenueTrendResponse r : list) {
                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(r.getPeriod());
                        Cell c1 = row.createCell(1); c1.setCellValue(r.getGrossRevenue().doubleValue()); c1.setCellStyle(currencyStyle);
                        Cell c2 = row.createCell(2); c2.setCellValue(r.getRefundAmount().doubleValue()); c2.setCellStyle(currencyStyle);
                        Cell c3 = row.createCell(3); c3.setCellValue(r.getNetRevenue().doubleValue()); c3.setCellStyle(currencyStyle);
                        row.createCell(4).setCellValue(r.getTicketCount());
                    }
                }
                case MOVIES -> {
                    String[] headers = {"Xếp hạng", "Mã phim", "Tên phim", "Doanh thu gộp (VND)", "Số tiền hoàn (VND)", "Doanh thu thuần (VND)", "Vé bán ra", "Vé hoàn", "Vé thuần"};
                    for (int i = 0; i < headers.length; i++) {
                        Cell c = headerRow.createCell(i);
                        c.setCellValue(headers[i]);
                        c.setCellStyle(headerStyle);
                    }
                    List<MovieReportResponse> list = getMovieReport(from, to, sortBy, limit);
                    for (MovieReportResponse r : list) {
                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(r.getRank());
                        row.createCell(1).setCellValue(r.getMovieId());
                        row.createCell(2).setCellValue(r.getMovieTitle());
                        Cell c3 = row.createCell(3); c3.setCellValue(r.getGrossRevenue().doubleValue()); c3.setCellStyle(currencyStyle);
                        Cell c4 = row.createCell(4); c4.setCellValue(r.getRefundAmount().doubleValue()); c4.setCellStyle(currencyStyle);
                        Cell c5 = row.createCell(5); c5.setCellValue(r.getNetRevenue().doubleValue()); c5.setCellStyle(currencyStyle);
                        row.createCell(6).setCellValue(r.getGrossTicketsSold());
                        row.createCell(7).setCellValue(r.getRefundedTickets());
                        row.createCell(8).setCellValue(r.getNetTicketsSold());
                    }
                }
                case CINEMAS -> {
                    String[] headers = {"Xếp hạng", "Mã rạp", "Tên rạp", "Thành phố", "Doanh thu gộp (VND)", "Số tiền hoàn (VND)", "Doanh thu thuần (VND)", "Vé bán ra", "Vé hoàn", "Vé thuần"};
                    for (int i = 0; i < headers.length; i++) {
                        Cell c = headerRow.createCell(i);
                        c.setCellValue(headers[i]);
                        c.setCellStyle(headerStyle);
                    }
                    List<CinemaReportResponse> list = getCinemaReport(from, to, sortBy, limit);
                    for (CinemaReportResponse r : list) {
                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(r.getRank());
                        row.createCell(1).setCellValue(r.getCinemaId());
                        row.createCell(2).setCellValue(r.getCinemaName());
                        row.createCell(3).setCellValue(r.getCity());
                        Cell c4 = row.createCell(4); c4.setCellValue(r.getGrossRevenue().doubleValue()); c4.setCellStyle(currencyStyle);
                        Cell c5 = row.createCell(5); c5.setCellValue(r.getRefundAmount().doubleValue()); c5.setCellStyle(currencyStyle);
                        Cell c6 = row.createCell(6); c6.setCellValue(r.getNetRevenue().doubleValue()); c6.setCellStyle(currencyStyle);
                        row.createCell(7).setCellValue(r.getGrossTicketsSold());
                        row.createCell(8).setCellValue(r.getRefundedTickets());
                        row.createCell(9).setCellValue(r.getNetTicketsSold());
                    }
                }
                case OCCUPANCY -> {
                    String[] headers = {"Mã suất chiếu", "Phim", "Rạp", "Phòng chiếu", "Bắt đầu", "Kết thúc", "Định dạng", "Tổng ghế", "Ghế đã đặt", "Ghế trống", "Tỷ lệ lấp đầy (%)"};
                    for (int i = 0; i < headers.length; i++) {
                        Cell c = headerRow.createCell(i);
                        c.setCellValue(headers[i]);
                        c.setCellStyle(headerStyle);
                    }
                    List<ShowtimeOccupancyResponse> list = calculateOccupancies(from, to, cinemaId, movieId);
                    for (ShowtimeOccupancyResponse r : list) {
                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(r.getShowtimeId());
                        row.createCell(1).setCellValue(r.getMovieTitle());
                        row.createCell(2).setCellValue(r.getCinemaName());
                        row.createCell(3).setCellValue(r.getAuditoriumName());
                        row.createCell(4).setCellValue(r.getStartTime().format(DATETIME_FORMATTER));
                        row.createCell(5).setCellValue(r.getEndTime().format(DATETIME_FORMATTER));
                        row.createCell(6).setCellValue(r.getFormat().name());
                        row.createCell(7).setCellValue(r.getTotalCapacity());
                        row.createCell(8).setCellValue(r.getOccupiedSeats());
                        row.createCell(9).setCellValue(r.getAvailableSeats());
                        row.createCell(10).setCellValue(r.getOccupancyRate().doubleValue());
                    }
                }
            }

            for (int i = 0; i < 11; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("[REPORT SERVICE] Error exporting XLSX: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi tạo file Excel báo cáo: " + e.getMessage(), e);
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }

    private Map<String, Integer> fetchAuditoriumCapacities(Set<String> auditoriumIds) {
        if (auditoriumIds == null || auditoriumIds.isEmpty()) return new HashMap<>();
        Map<String, Integer> map = new HashMap<>();
        List<String> idList = new ArrayList<>(auditoriumIds);
        int batchSize = 1000;
        for (int i = 0; i < idList.size(); i += batchSize) {
            List<String> sub = idList.subList(i, Math.min(i + batchSize, idList.size()));
            List<Object[]> rows = seatRepository.sumCapacityByAuditoriumIds(sub);
            if (rows != null) {
                for (Object[] r : rows) {
                    map.put((String) r[0], toInt(r[1]));
                }
            }
        }
        return map;
    }

    private Map<String, Integer> fetchOccupiedCapacities(Set<String> showtimeIds) {
        if (showtimeIds == null || showtimeIds.isEmpty()) return new HashMap<>();
        Map<String, Integer> map = new HashMap<>();
        List<String> idList = new ArrayList<>(showtimeIds);
        int batchSize = 1000;
        for (int i = 0; i < idList.size(); i += batchSize) {
            List<String> sub = idList.subList(i, Math.min(i + batchSize, idList.size()));
            List<Object[]> rows = ticketRepository.sumOccupiedCapacityByShowtimeIds(sub);
            if (rows != null) {
                for (Object[] r : rows) {
                    map.put((String) r[0], toInt(r[1]));
                }
            }
        }
        return map;
    }

    private ShowtimeOccupancyResponse mapShowtimeOccupancyRow(Object[] row) {
        String showtimeId = (String) row[0];
        String movieId = (String) row[1];
        String movieTitle = (String) row[2];
        String cinemaId = (String) row[3];
        String cinemaName = (String) row[4];
        String audId = (String) row[5];
        String audName = (String) row[6];
        LocalDateTime startTime = toLocalDateTime(row[7]);
        LocalDateTime endTime = toLocalDateTime(row[8]);
        ShowtimeFormat format = ShowtimeFormat.TWO_D;
        if (row[9] != null) {
            try {
                format = ShowtimeFormat.valueOf(row[9].toString());
            } catch (Exception ignored) {
            }
        }
        int totalCapacity = toInt(row[10]);
        int occupiedSeats = toInt(row[11]);
        int availableSeats = toInt(row[12]);
        BigDecimal occupancyRate = toBigDecimal(row[13]).setScale(2, RoundingMode.HALF_UP);

        return ShowtimeOccupancyResponse.builder()
                .showtimeId(showtimeId)
                .movieId(movieId)
                .movieTitle(movieTitle)
                .cinemaId(cinemaId)
                .cinemaName(cinemaName)
                .auditoriumId(audId)
                .auditoriumName(audName)
                .startTime(startTime)
                .endTime(endTime)
                .format(format)
                .totalCapacity(totalCapacity)
                .occupiedSeats(occupiedSeats)
                .availableSeats(availableSeats)
                .occupancyRate(occupancyRate)
                .build();
    }

    private static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        return new BigDecimal(obj.toString());
    }

    private static long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number num) return num.longValue();
        return Long.parseLong(obj.toString());
    }

    private static int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number num) return num.intValue();
        return Integer.parseInt(obj.toString());
    }

    private static LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime ldt) return ldt;
        if (obj instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return LocalDateTime.parse(obj.toString().replace(" ", "T"));
    }

    private static Object[] extractSingleRow(Object[] row) {
        if (row == null || row.length == 0) return null;
        if (row[0] instanceof Object[] nested) return nested;
        return row;
    }

    private static class TrendAccumulator {
        String period;
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        long ticketCount = 0;

        TrendAccumulator(String period) {
            this.period = period;
        }
    }

    private static class MovieAccumulator {
        String movieId;
        String title;
        String posterUrl;
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        long grossTicketsSold = 0;
        long refundedTickets = 0;
        long totalBookings = 0;

        MovieAccumulator(String movieId, String title, String posterUrl) {
            this.movieId = movieId;
            this.title = title != null ? title : "";
            this.posterUrl = posterUrl;
        }
    }

    private static class CinemaAccumulator {
        String cinemaId;
        String name;
        String city;
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        long grossTicketsSold = 0;
        long refundedTickets = 0;
        long totalShowtimes = 0;

        CinemaAccumulator(String cinemaId, String name, String city) {
            this.cinemaId = cinemaId;
            this.name = name != null ? name : "";
            this.city = city != null ? city : "";
        }
    }

    private static class DateRange {
        LocalDateTime from;
        LocalDateTime to;

        DateRange(LocalDateTime from, LocalDateTime to) {
            this.from = from;
            this.to = to;
        }
    }
}
