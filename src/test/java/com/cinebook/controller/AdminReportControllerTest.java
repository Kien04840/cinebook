package com.cinebook.controller;

import com.cinebook.dto.response.*;
import com.cinebook.enums.ReportFormat;
import com.cinebook.enums.ReportGroupBy;
import com.cinebook.enums.ReportSortBy;
import com.cinebook.enums.ReportType;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminReportControllerTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private AdminReportController adminReportController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminReportController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/reports/dashboard - Returns 200 with dashboard summary KPIs")
    void getDashboard_Returns200() throws Exception {
        DashboardResponse mockDashboard = DashboardResponse.builder()
                .from(LocalDateTime.of(2026, 8, 1, 0, 0))
                .to(LocalDateTime.of(2026, 8, 31, 23, 59, 59))
                .financial(FinancialKpiResponse.builder()
                        .grossRevenue(new BigDecimal("1000000.00"))
                        .refundAmount(BigDecimal.ZERO)
                        .netRevenue(new BigDecimal("1000000.00"))
                        .build())
                .tickets(TicketKpiResponse.builder()
                        .grossTicketsSold(10L)
                        .refundedTickets(0L)
                        .netTicketsSold(10L)
                        .build())
                .bookings(BookingKpiResponse.builder()
                        .totalBookings(5L)
                        .paidBookings(5L)
                        .build())
                .users(UserKpiResponse.builder()
                        .totalUsers(100L)
                        .activeUsers(98L)
                        .build())
                .operations(OperationKpiResponse.builder()
                        .totalShowtimes(20L)
                        .averageOccupancyRate(new BigDecimal("75.50"))
                        .build())
                .build();

        when(reportService.getDashboardSummary(any(), any())).thenReturn(mockDashboard);

        mockMvc.perform(get("/api/v1/admin/reports/dashboard")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financial.grossRevenue").value(1000000.00))
                .andExpect(jsonPath("$.financial.netRevenue").value(1000000.00))
                .andExpect(jsonPath("$.tickets.netTicketsSold").value(10))
                .andExpect(jsonPath("$.users.totalUsers").value(100));
    }

    @Test
    @DisplayName("GET /api/v1/admin/reports/revenue - Returns 200 with trend data")
    void getRevenueTrend_Returns200() throws Exception {
        RevenueTrendResponse trend = RevenueTrendResponse.builder()
                .period("2026-08-15")
                .grossRevenue(new BigDecimal("500000.00"))
                .refundAmount(BigDecimal.ZERO)
                .netRevenue(new BigDecimal("500000.00"))
                .ticketCount(5L)
                .build();

        when(reportService.getRevenueTrend(any(), any(), eq(ReportGroupBy.DAY))).thenReturn(List.of(trend));

        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .param("groupBy", "DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].period").value("2026-08-15"))
                .andExpect(jsonPath("$[0].grossRevenue").value(500000.00));
    }

    @Test
    @DisplayName("GET /api/v1/admin/reports/movies - Returns 200 with movie rankings")
    void getMovieReport_Returns200() throws Exception {
        MovieReportResponse movie = MovieReportResponse.builder()
                .rank(1)
                .movieId("mov-123")
                .movieTitle("Inception")
                .grossRevenue(new BigDecimal("1000000.00"))
                .refundAmount(BigDecimal.ZERO)
                .netRevenue(new BigDecimal("1000000.00"))
                .grossTicketsSold(10L)
                .refundedTickets(0L)
                .netTicketsSold(10L)
                .build();

        when(reportService.getMovieReport(any(), any(), any(), any())).thenReturn(List.of(movie));

        mockMvc.perform(get("/api/v1/admin/reports/movies")
                        .param("sortBy", "REVENUE")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].movieTitle").value("Inception"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/reports/export?format=CSV - Returns 200 with CSV headers")
    void exportCsv_Returns200() throws Exception {
        byte[] dummyCsv = "header1,header2".getBytes();
        when(reportService.exportReport(any(), eq(ReportFormat.CSV), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dummyCsv);
        when(reportService.getExportFilename(any(), eq(ReportFormat.CSV)))
                .thenReturn("revenue-report.csv");

        mockMvc.perform(get("/api/v1/admin/reports/export")
                        .param("reportType", "REVENUE")
                        .param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"revenue-report.csv\""))
                .andExpect(content().contentType("text/csv;charset=UTF-8"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/reports/export?format=XLSX - Returns 200 with XLSX headers")
    void exportXlsx_Returns200() throws Exception {
        byte[] dummyXlsx = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(reportService.exportReport(any(), eq(ReportFormat.XLSX), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dummyXlsx);
        when(reportService.getExportFilename(any(), eq(ReportFormat.XLSX)))
                .thenReturn("revenue-report.xlsx");

        mockMvc.perform(get("/api/v1/admin/reports/export")
                        .param("reportType", "REVENUE")
                        .param("format", "XLSX"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"revenue-report.xlsx\""))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}

