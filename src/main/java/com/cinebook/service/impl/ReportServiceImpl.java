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
        long grossTickets = ticketRepository.countGrossTicketsBetween(range.from, range.to);
        long refundedTickets = ticketRepository.countRefundedTicketsBetween(range.from, range.to);
        long netTickets = Math.max(0, grossTickets - refundedTickets);

        TicketKpiResponse tickets = TicketKpiResponse.builder()
                .grossTicketsSold(grossTickets)
                .refundedTickets(refundedTickets)
                .netTicketsSold(netTickets)
                .build();

        // 3. Bookings
        long totalBookings = bookingRepository.countByCreatedAtBetween(range.from, range.to);
        long paidBookings = bookingRepository.countByBookingStatusAndCreatedAtBetween(BookingStatus.PAID, range.from, range.to);
        long cancelledBookings = bookingRepository.countByBookingStatusAndCreatedAtBetween(BookingStatus.CANCELLED, range.from, range.to);
        long expiredBookings = bookingRepository.countByBookingStatusAndCreatedAtBetween(BookingStatus.EXPIRED, range.from, range.to);
        long refundedBookings = bookingRepository.countByBookingStatusAndCreatedAtBetween(BookingStatus.REFUNDED, range.from, range.to);

        BookingKpiResponse bookings = BookingKpiResponse.builder()
                .totalBookings(totalBookings)
                .paidBookings(paidBookings)
                .cancelledBookings(cancelledBookings)
                .expiredBookings(expiredBookings)
                .refundedBookings(refundedBookings)
                .build();

        // 4. Users
        long totalUsers = userRepository.count();
        long newUsers = userRepository.countByCreatedAtBetween(range.from, range.to);
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long blockedUsers = userRepository.countByStatus(UserStatus.BLOCKED);

        UserKpiResponse users = UserKpiResponse.builder()
                .totalUsers(totalUsers)
                .newUsersInPeriod(newUsers)
                .activeUsers(activeUsers)
                .blockedUsers(blockedUsers)
                .build();

        // 5. Operations / Occupancy
        List<ShowtimeOccupancyResponse> occupancies = calculateOccupancies(range.from, range.to, null, null);
        long totalShowtimes = occupancies.size();
        BigDecimal avgOccupancy = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (!occupancies.isEmpty()) {
            BigDecimal sumRate = occupancies.stream()
                    .map(ShowtimeOccupancyResponse::getOccupancyRate)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgOccupancy = sumRate.divide(BigDecimal.valueOf(occupancies.size()), 2, RoundingMode.HALF_UP);
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
        DateTimeFormatter formatter = effectiveGroupBy == ReportGroupBy.MONTH ? MONTH_FORMATTER : DAY_FORMATTER;

        Map<String, TrendAccumulator> trendMap = new TreeMap<>();

        // Group payments
        List<Payment> payments = paymentRepository.findSuccessfulPaymentsBetween(range.from, range.to);
        for (Payment p : payments) {
            if (p.getPaidAt() != null) {
                String period = p.getPaidAt().format(formatter);
                TrendAccumulator acc = trendMap.computeIfAbsent(period, k -> new TrendAccumulator(k));
                acc.grossRevenue = acc.grossRevenue.add(p.getAmount());
            }
        }

        // Group refunds
        List<Refund> refunds = refundRepository.findSuccessfulRefundsBetween(range.from, range.to);
        for (Refund r : refunds) {
            if (r.getProcessedAt() != null) {
                String period = r.getProcessedAt().format(formatter);
                TrendAccumulator acc = trendMap.computeIfAbsent(period, k -> new TrendAccumulator(k));
                acc.refundAmount = acc.refundAmount.add(r.getAmount());
            }
        }

        // Group tickets
        List<Ticket> tickets = ticketRepository.findSoldTicketsBetween(range.from, range.to);
        for (Ticket t : tickets) {
            LocalDateTime eventTime = t.getCreatedAt();
            if (eventTime != null) {
                String period = eventTime.format(formatter);
                TrendAccumulator acc = trendMap.computeIfAbsent(period, k -> new TrendAccumulator(k));
                if (t.getTicketStatus() != TicketStatus.CANCELLED) {
                    acc.ticketCount++;
                }
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

        // 1. Payments
        List<Payment> payments = paymentRepository.findSuccessfulPaymentsBetween(range.from, range.to);
        for (Payment p : payments) {
            Movie movie = p.getBooking().getShowtime().getMovie();
            if (movie != null) {
                MovieAccumulator acc = movieMap.computeIfAbsent(movie.getId(), k -> new MovieAccumulator(movie));
                acc.grossRevenue = acc.grossRevenue.add(p.getAmount());
            }
        }

        // 2. Refunds
        List<Refund> refunds = refundRepository.findSuccessfulRefundsBetween(range.from, range.to);
        for (Refund r : refunds) {
            Movie movie = r.getPayment().getBooking().getShowtime().getMovie();
            if (movie != null) {
                MovieAccumulator acc = movieMap.computeIfAbsent(movie.getId(), k -> new MovieAccumulator(movie));
                acc.refundAmount = acc.refundAmount.add(r.getAmount());
            }
        }

        // 3. Tickets
        List<Ticket> tickets = ticketRepository.findSoldTicketsBetween(range.from, range.to);
        for (Ticket t : tickets) {
            Movie movie = t.getBooking().getShowtime().getMovie();
            if (movie != null) {
                MovieAccumulator acc = movieMap.computeIfAbsent(movie.getId(), k -> new MovieAccumulator(movie));
                acc.grossTicketsSold++;
                if (t.getTicketStatus() == TicketStatus.CANCELLED) {
                    acc.refundedTickets++;
                }
            }
        }

        List<MovieAccumulator> list = new ArrayList<>(movieMap.values());

        // Sort
        if (effectiveSort == ReportSortBy.TICKETS) {
            list.sort((a, b) -> {
                long netA = a.grossTicketsSold - a.refundedTickets;
                long netB = b.grossTicketsSold - b.refundedTickets;
                int cmp = Long.compare(netB, netA);
                if (cmp != 0) return cmp;
                return a.movie.getTitle().compareToIgnoreCase(b.movie.getTitle());
            });
        } else {
            list.sort((a, b) -> {
                BigDecimal netA = a.grossRevenue.subtract(a.refundAmount);
                BigDecimal netB = b.grossRevenue.subtract(b.refundAmount);
                int cmp = netB.compareTo(netA);
                if (cmp != 0) return cmp;
                return a.movie.getTitle().compareToIgnoreCase(b.movie.getTitle());
            });
        }

        if (limit != null && limit > 0 && limit < list.size()) {
            list = list.subList(0, limit);
        }

        List<MovieReportResponse> results = new ArrayList<>();
        int rank = 1;
        for (MovieAccumulator acc : list) {
            BigDecimal netRev = acc.grossRevenue.subtract(acc.refundAmount).setScale(2, RoundingMode.HALF_UP);
            long netTix = Math.max(0, acc.grossTicketsSold - acc.refundedTickets);

            results.add(MovieReportResponse.builder()
                    .rank(rank++)
                    .movieId(acc.movie.getId())
                    .movieTitle(acc.movie.getTitle())
                    .posterUrl(acc.movie.getPosterUrl())
                    .grossRevenue(acc.grossRevenue.setScale(2, RoundingMode.HALF_UP))
                    .refundAmount(acc.refundAmount.setScale(2, RoundingMode.HALF_UP))
                    .netRevenue(netRev)
                    .grossTicketsSold(acc.grossTicketsSold)
                    .refundedTickets(acc.refundedTickets)
                    .netTicketsSold(netTix)
                    .build());
        }

        return results;
    }

    @Override
    public List<CinemaReportResponse> getCinemaReport(LocalDateTime from, LocalDateTime to, ReportSortBy sortBy, Integer limit) {
        DateRange range = normalizeDateRange(from, to);
        ReportSortBy effectiveSort = (sortBy == ReportSortBy.TICKETS) ? ReportSortBy.TICKETS : ReportSortBy.REVENUE;

        Map<String, CinemaAccumulator> cinemaMap = new HashMap<>();

        // 1. Payments
        List<Payment> payments = paymentRepository.findSuccessfulPaymentsBetween(range.from, range.to);
        for (Payment p : payments) {
            Cinema cinema = p.getBooking().getShowtime().getAuditorium().getCinema();
            if (cinema != null) {
                CinemaAccumulator acc = cinemaMap.computeIfAbsent(cinema.getId(), k -> new CinemaAccumulator(cinema));
                acc.grossRevenue = acc.grossRevenue.add(p.getAmount());
            }
        }

        // 2. Refunds
        List<Refund> refunds = refundRepository.findSuccessfulRefundsBetween(range.from, range.to);
        for (Refund r : refunds) {
            Cinema cinema = r.getPayment().getBooking().getShowtime().getAuditorium().getCinema();
            if (cinema != null) {
                CinemaAccumulator acc = cinemaMap.computeIfAbsent(cinema.getId(), k -> new CinemaAccumulator(cinema));
                acc.refundAmount = acc.refundAmount.add(r.getAmount());
            }
        }

        // 3. Tickets
        List<Ticket> tickets = ticketRepository.findSoldTicketsBetween(range.from, range.to);
        for (Ticket t : tickets) {
            Cinema cinema = t.getBooking().getShowtime().getAuditorium().getCinema();
            if (cinema != null) {
                CinemaAccumulator acc = cinemaMap.computeIfAbsent(cinema.getId(), k -> new CinemaAccumulator(cinema));
                acc.grossTicketsSold++;
                if (t.getTicketStatus() == TicketStatus.CANCELLED) {
                    acc.refundedTickets++;
                }
            }
        }

        List<CinemaAccumulator> list = new ArrayList<>(cinemaMap.values());

        // Sort
        if (effectiveSort == ReportSortBy.TICKETS) {
            list.sort((a, b) -> {
                long netA = a.grossTicketsSold - a.refundedTickets;
                long netB = b.grossTicketsSold - b.refundedTickets;
                int cmp = Long.compare(netB, netA);
                if (cmp != 0) return cmp;
                return a.cinema.getName().compareToIgnoreCase(b.cinema.getName());
            });
        } else {
            list.sort((a, b) -> {
                BigDecimal netA = a.grossRevenue.subtract(a.refundAmount);
                BigDecimal netB = b.grossRevenue.subtract(b.refundAmount);
                int cmp = netB.compareTo(netA);
                if (cmp != 0) return cmp;
                return a.cinema.getName().compareToIgnoreCase(b.cinema.getName());
            });
        }

        if (limit != null && limit > 0 && limit < list.size()) {
            list = list.subList(0, limit);
        }

        List<CinemaReportResponse> results = new ArrayList<>();
        int rank = 1;
        for (CinemaAccumulator acc : list) {
            BigDecimal netRev = acc.grossRevenue.subtract(acc.refundAmount).setScale(2, RoundingMode.HALF_UP);
            long netTix = Math.max(0, acc.grossTicketsSold - acc.refundedTickets);

            results.add(CinemaReportResponse.builder()
                    .rank(rank++)
                    .cinemaId(acc.cinema.getId())
                    .cinemaName(acc.cinema.getName())
                    .city(acc.cinema.getCity())
                    .grossRevenue(acc.grossRevenue.setScale(2, RoundingMode.HALF_UP))
                    .refundAmount(acc.refundAmount.setScale(2, RoundingMode.HALF_UP))
                    .netRevenue(netRev)
                    .grossTicketsSold(acc.grossTicketsSold)
                    .refundedTickets(acc.refundedTickets)
                    .netTicketsSold(netTix)
                    .build());
        }

        return results;
    }

    private List<ShowtimeOccupancyResponse> calculateOccupancies(LocalDateTime from, LocalDateTime to, String cinemaId, String movieId) {
        List<Showtime> showtimes = showtimeRepository.findActiveShowtimesForReport(from, to, cinemaId, movieId);
        if (showtimes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> auditoriumCapacityMap = new HashMap<>();
        List<ShowtimeOccupancyResponse> results = new ArrayList<>();

        for (Showtime s : showtimes) {
            String audId = s.getAuditorium().getId();
            int capacity = auditoriumCapacityMap.computeIfAbsent(audId,
                    id -> (int) seatRepository.countByAuditoriumIdAndStatus(id, SeatStatus.ACTIVE));

            List<Ticket> occupiedTickets = ticketRepository.findTicketsByShowtimeIdAndStatuses(
                    s.getId(), List.of(TicketStatus.VALID, TicketStatus.USED));
            int occupiedSeats = occupiedTickets.size();
            int availableSeats = Math.max(0, capacity - occupiedSeats);

            BigDecimal occupancyRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (capacity > 0) {
                occupancyRate = BigDecimal.valueOf(occupiedSeats)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
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
                    .totalCapacity(capacity)
                    .occupiedSeats(occupiedSeats)
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
        List<ShowtimeOccupancyResponse> list = calculateOccupancies(range.from, range.to, cinemaId, movieId);

        if (sortBy == ReportSortBy.OCCUPANCY_RATE) {
            list.sort((a, b) -> {
                int cmp = b.getOccupancyRate().compareTo(a.getOccupancyRate());
                if (cmp != 0) return cmp;
                return a.getStartTime().compareTo(b.getStartTime());
            });
        } else {
            list.sort(Comparator.comparing(ShowtimeOccupancyResponse::getStartTime));
        }

        int pageSize = pageable.getPageSize() > 0 ? pageable.getPageSize() : 20;
        int pageNumber = pageable.getPageNumber() >= 0 ? pageable.getPageNumber() : 0;
        int start = Math.min(pageNumber * pageSize, list.size());
        int end = Math.min(start + pageSize, list.size());

        List<ShowtimeOccupancyResponse> pageContent = list.subList(start, end);
        PageImpl<ShowtimeOccupancyResponse> page = new PageImpl<>(pageContent, pageable, list.size());

        return PageResponse.of(page);
    }


    @Override
    public List<ShowtimeOccupancyResponse> getTopOccupancyShowtimes(LocalDateTime from, LocalDateTime to, Integer limit) {
        DateRange range = normalizeDateRange(from, to);
        int maxLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 10;

        List<ShowtimeOccupancyResponse> list = calculateOccupancies(range.from, range.to, null, null);
        list.sort((a, b) -> {
            int cmp = b.getOccupancyRate().compareTo(a.getOccupancyRate());
            if (cmp != 0) return cmp;
            return a.getStartTime().compareTo(b.getStartTime());
        });

        if (list.size() > maxLimit) {
            return list.subList(0, maxLimit);
        }
        return list;
    }

    @Override
    public BookingStatisticsResponse getBookingStatistics(LocalDateTime from, LocalDateTime to) {
        DateRange range = normalizeDateRange(from, to);

        long total = bookingRepository.countByCreatedAtBetween(range.from, range.to);
        long paid = bookingRepository.countByBookingStatusAndCreatedAtBetween(BookingStatus.PAID, range.from, range.to);
        long cancelled = bookingRepository.countByBookingStatusAndCreatedAtBetween(BookingStatus.CANCELLED, range.from, range.to);
        long expired = bookingRepository.countByBookingStatusAndCreatedAtBetween(BookingStatus.EXPIRED, range.from, range.to);
        long refunded = bookingRepository.countByBookingStatusAndCreatedAtBetween(BookingStatus.REFUNDED, range.from, range.to);
        BigDecimal amount = bookingRepository.findTotalBookingAmountBetween(range.from, range.to);
        if (amount == null) amount = BigDecimal.ZERO;

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

        long total = userRepository.count();
        long newUsers = userRepository.countByCreatedAtBetween(range.from, range.to);
        long active = userRepository.countByStatus(UserStatus.ACTIVE);
        long blocked = userRepository.countByStatus(UserStatus.BLOCKED);

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

        long total = refundRepository.countByProcessedAtBetween(range.from, range.to);
        long successful = refundRepository.countByRefundStatusAndProcessedAtBetween(RefundStatus.SUCCESS, range.from, range.to);
        long failed = refundRepository.countByRefundStatusAndProcessedAtBetween(RefundStatus.FAILED, range.from, range.to);
        long pending = refundRepository.countByRefundStatusAndProcessedAtBetween(RefundStatus.PENDING, range.from, range.to);
        BigDecimal amount = refundRepository.findTotalRefundAmountBetween(range.from, range.to);
        if (amount == null) amount = BigDecimal.ZERO;

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
        ReportType type = reportType != null ? reportType : ReportType.REVENUE;
        ReportFormat fmt = format != null ? format : ReportFormat.CSV;

        if (fmt == ReportFormat.XLSX) {
            return exportXlsx(type, from, to, groupBy, sortBy, cinemaId, movieId, limit);
        } else {
            return exportCsv(type, from, to, groupBy, sortBy, cinemaId, movieId, limit);
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
        Movie movie;
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        long grossTicketsSold = 0;
        long refundedTickets = 0;

        MovieAccumulator(Movie movie) {
            this.movie = movie;
        }
    }

    private static class CinemaAccumulator {
        Cinema cinema;
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        long grossTicketsSold = 0;
        long refundedTickets = 0;

        CinemaAccumulator(Cinema cinema) {
            this.cinema = cinema;
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
