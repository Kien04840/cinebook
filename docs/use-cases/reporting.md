# Canonical Specification: Reporting & Admin Dashboard Module

## 1. Overview & Scope

The **Reporting & Admin Dashboard Module** provides comprehensive, read-only business analytics, executive KPIs, revenue trends, movie/cinema performance metrics, occupancy tracking, and report exports (CSV / XLSX) for CineBook system administrators (`ROLE_ADMIN`).

---

## 2. Actors & Permissions

* **Actor**: `Administrator` (`ROLE_ADMIN`).
* **Security & RBAC**:
  * Anonymous access $\rightarrow$ `401 Unauthorized`.
  * Non-admin customer access $\rightarrow$ `403 Forbidden`.
  * All reporting endpoints are placed strictly under `/api/v1/admin/reports/**`.
  * Zero exposure of sensitive customer data (passwords, tokens, phone/email hashes).

---

## 3. Core Business & Metric Definitions

### 3.1 Financial & Revenue Invariants
With the integration of Payment V2 + Refund, financial calculations are strictly defined as follows:

* **Gross Revenue**:
  * Total monetary value of all successfully completed payment transactions within the filter period.
  * Formally: $\sum \text{payment.amount}$ where $\text{payment.paymentStatus} \in \{\text{SUCCESS}, \text{REFUNDED}\}$.
  * *Rationale*: A payment that is refunded later still represents an initial successful purchase. Failed, pending, or cancelled payment attempts are excluded.
* **Successful Refund Amount**:
  * Total monetary value of all successfully processed refunds within the filter period.
  * Formally: $\sum \text{refund.amount}$ where $\text{refund.refundStatus} = \text{SUCCESS}$.
* **Net Revenue**:
  * The actual retained revenue for the cinema platform.
  * Formally: $\text{Net Revenue} = \text{Gross Revenue} - \text{Successful Refund Amount}$.
  * All financial computations use `java.math.BigDecimal` with deterministic scale and rounding (`RoundingMode.HALF_UP`).

### 3.2 Ticket Sales Invariants
* **Gross Tickets Sold**:
  * Count of all tickets issued for bookings that reached `PAID` or `REFUNDED` status.
* **Refunded Tickets**:
  * Count of tickets belonging to `REFUNDED` bookings where `ticket.ticketStatus = CANCELLED`.
* **Net Tickets Sold**:
  * $\text{Net Tickets Sold} = \text{Gross Tickets Sold} - \text{Refunded Tickets}$.

### 3.3 Showtime Occupancy Invariants
* **Total Capacity**:
  * Count of `ACTIVE` seats in the auditorium associated with the showtime:
    $$\text{Total Capacity} = \text{COUNT}(\text{Seat}) \quad \text{where} \; \text{seat.auditorium.id} = \text{showtime.auditorium.id} \; \text{AND} \; \text{seat.status} = \text{ACTIVE}$$
* **Occupied Seats**:
  * Count of tickets currently holding or used for a seat:
    $$\text{Occupied Seats} = \text{COUNT}(\text{Ticket}) \quad \text{where} \; \text{ticket.showtime.id} = \text{showtime.id} \; \text{AND} \; \text{ticket.ticketStatus} \in \{\text{VALID}, \text{USED}\}$$
  * *Note*: `CANCELLED` tickets do NOT occupy seats.
* **Available Seats**:
  * $\text{Available Seats} = \max(0, \text{Total Capacity} - \text{Occupied Seats})$.
* **Occupancy Rate**:
  * $\text{Occupancy Rate} = \frac{\text{Occupied Seats}}{\text{Total Capacity}} \times 100\%$ (rounded to 2 decimal places).
  * If $\text{Total Capacity} = 0$, $\text{Occupancy Rate} = 0.00\%$.

### 3.4 Booking Statistics
* Categorized by finalized `BookingStatus`:
  * `totalBookings`: Total count of bookings created within the period.
  * `paidBookings`: Count of bookings with `bookingStatus = PAID`.
  * `cancelledBookings`: Count of bookings with `bookingStatus = CANCELLED`.
  * `expiredBookings`: Count of bookings with `bookingStatus = EXPIRED`.
  * `refundedBookings`: Count of bookings with `bookingStatus = REFUNDED`.

### 3.5 User Statistics
* Categorized by finalized `UserStatus`:
  * `totalUsers`: Total registered users in the database.
  * `newUsersInPeriod`: Users with `createdAt` between `from` and `to`.
  * `activeUsers`: Users with `status = ACTIVE`.
  * `blockedUsers`: Users with `status = BLOCKED`.

### 3.6 Refund Statistics
* Categorized by finalized `RefundStatus`:
  * `totalRefunds`: Total refund records created within the period.
  * `successfulRefunds`: Refunds with `refundStatus = SUCCESS`.
  * `failedRefunds`: Refunds with `refundStatus = FAILED`.
  * `pendingRefunds`: Refunds with `refundStatus = PENDING`.
  * `totalRefundAmount`: Total amount of `SUCCESS` refunds.

---

## 4. Time Filters & Validation

* **Parameters**: `from` (ISO Date or DateTime, e.g. `2026-08-01` or `2026-08-01T00:00:00`), `to` (ISO Date or DateTime, e.g. `2026-08-31` or `2026-08-31T23:59:59`).
* **Default Behavior**:
  * If `from` is null: defaults to the 1st day of current month at `00:00:00`.
  * If `to` is null: defaults to current date at `23:59:59.999999999`.
* **Validation**:
  * If `from > to`: returns `400 Bad Request` with message: *"Ngày bắt đầu không được lớn hơn ngày kết thúc."*

---

## 5. REST API Specifications

All endpoints require `Authorization: Bearer <ADMIN_JWT_TOKEN>`.

### 5.1 Dashboard Summary
* **Endpoint**: `GET /api/v1/admin/reports/dashboard`
* **Query Parameters**: `from`, `to`
* **Response**: `DashboardResponse`
  * `financial`: `grossRevenue`, `refundAmount`, `netRevenue`
  * `tickets`: `grossTicketsSold`, `refundedTickets`, `netTicketsSold`
  * `bookings`: `totalBookings`, `paidBookings`, `cancelledBookings`, `expiredBookings`, `refundedBookings`
  * `users`: `totalUsers`, `newUsersInPeriod`, `activeUsers`, `blockedUsers`
  * `operations`: `totalShowtimes`, `averageOccupancyRate`

### 5.2 Revenue Trends
* **Endpoint**: `GET /api/v1/admin/reports/revenue`
* **Query Parameters**: `from`, `to`, `groupBy` (`DAY`, `MONTH`)
* **Response**: `List<RevenueTrendResponse>`
  * Each entry contains: `period` (YYYY-MM-DD or YYYY-MM), `grossRevenue`, `refundAmount`, `netRevenue`, `ticketCount`.

### 5.3 Movie Performance & Ranking
* **Endpoint**: `GET /api/v1/admin/reports/movies`
* **Query Parameters**: `from`, `to`, `sortBy` (`REVENUE`, `TICKETS`), `limit`
* **Response**: `List<MovieReportResponse>`
  * Each entry contains: `rank`, `movieId`, `movieTitle`, `posterUrl`, `grossRevenue`, `refundAmount`, `netRevenue`, `grossTicketsSold`, `refundedTickets`, `netTicketsSold`.

### 5.4 Cinema Performance & Ranking
* **Endpoint**: `GET /api/v1/admin/reports/cinemas`
* **Query Parameters**: `from`, `to`, `sortBy` (`REVENUE`, `TICKETS`), `limit`
* **Response**: `List<CinemaReportResponse>`
  * Each entry contains: `rank`, `cinemaId`, `cinemaName`, `city`, `grossRevenue`, `refundAmount`, `netRevenue`, `grossTicketsSold`, `refundedTickets`, `netTicketsSold`.

### 5.5 Showtime Occupancy Report
* **Endpoint**: `GET /api/v1/admin/reports/showtimes/occupancy`
* **Query Parameters**: `from`, `to`, `cinemaId`, `movieId`, `sortBy` (`START_TIME`, `OCCUPANCY_RATE`), `page`, `size`
* **Response**: `PageResponse<ShowtimeOccupancyResponse>`
  * Each entry contains: `showtimeId`, `movieId`, `movieTitle`, `cinemaId`, `cinemaName`, `auditoriumId`, `auditoriumName`, `startTime`, `endTime`, `format`, `totalCapacity`, `occupiedSeats`, `availableSeats`, `occupancyRate`.

### 5.6 Top Occupied Showtimes
* **Endpoint**: `GET /api/v1/admin/reports/showtimes/top-occupancy`
* **Query Parameters**: `from`, `to`, `limit` (default 10)
* **Response**: `List<ShowtimeOccupancyResponse>` sorted by `occupancyRate` DESC.

### 5.7 Booking Statistics
* **Endpoint**: `GET /api/v1/admin/reports/bookings`
* **Query Parameters**: `from`, `to`
* **Response**: `BookingStatisticsResponse`

### 5.8 User Statistics
* **Endpoint**: `GET /api/v1/admin/reports/users`
* **Query Parameters**: `from`, `to`
* **Response**: `UserStatisticsResponse`

### 5.9 Refund Statistics
* **Endpoint**: `GET /api/v1/admin/reports/refunds`
* **Query Parameters**: `from`, `to`
* **Response**: `RefundStatisticsResponse`

### 5.10 Report Export (CSV & XLSX)
* **Endpoint**: `GET /api/v1/admin/reports/export`
* **Query Parameters**:
  * `reportType`: `REVENUE`, `MOVIES`, `CINEMAS`, `OCCUPANCY`
  * `format`: `CSV`, `XLSX`
  * `from`, `to`, `groupBy`, `sortBy`, `cinemaId`, `movieId`
* **Response**: Binary stream (`byte[]`)
  * `Content-Type`: `text/csv; charset=UTF-8` or `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  * `Content-Disposition`: `attachment; filename="<report-name>-<timestamp>.<ext>"`

---

## 6. Report Export Specifications

* **CSV Format**:
  * UTF-8 encoded with standard comma delimiters and quoted fields (RFC 4180).
  * Includes UTF-8 BOM (`\uFEFF`) to ensure Microsoft Excel correctly displays Vietnamese characters.
* **XLSX Format**:
  * Created using standard Apache POI (`poi-ooxml`).
  * Styled with professional headers, bold titles, date formatting, and formatted currency cells (`#,##0 "VND"`).
  * Auto-sized column widths for readability.

---

## 7. Performance & Aggregation Strategy

* Use JPA projections and JPQL aggregation queries (`SUM`, `COUNT`, `GROUP BY`) to process metrics directly on MySQL 8.
* Zero loading of massive unpaginated entity lists into application memory.
* Zero schema modifications: all analytical data queries operate on existing normalized tables (`payments`, `refunds`, `bookings`, `tickets`, `showtimes`, `auditoriums`, `cinemas`, `seats`, `movies`, `users`).

