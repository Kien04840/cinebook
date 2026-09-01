export interface FinancialKpiResponse {
  grossRevenue: number
  refundAmount: number
  netRevenue: number
}

export interface TicketKpiResponse {
  grossTicketsSold: number
  refundedTickets: number
  netTicketsSold: number
}

export interface BookingKpiResponse {
  totalBookings: number
  paidBookings: number
  cancelledBookings: number
  expiredBookings: number
  refundedBookings: number
}

export interface UserKpiResponse {
  totalUsers?: number
  activeUsers?: number
  newUsersPeriod?: number
}

export interface OperationKpiResponse {
  totalShowtimes: number
  averageOccupancyRate: number
}

export interface DashboardResponse {
  from: string
  to: string
  financial: FinancialKpiResponse
  tickets: TicketKpiResponse
  bookings: BookingKpiResponse
  users?: UserKpiResponse
  operations: OperationKpiResponse
}

export interface UserStatisticsResponse {
  from?: string
  to?: string
  totalUsers: number
  newUsersInPeriod: number
  activeUsers: number
  blockedUsers: number
}

export interface RevenueTrendResponse {
  period: string
  grossRevenue: number
  refundAmount: number
  netRevenue: number
  ticketCount?: number
  bookingsCount?: number
}

export interface MovieReportResponse {
  movieId: string
  movieTitle: string
  posterUrl?: string
  totalBookings: number
  ticketsSold: number
  totalRevenue: number
  occupancyRate?: number
}

export interface CinemaReportResponse {
  cinemaId: string
  cinemaName: string
  city: string
  totalShowtimes: number
  ticketsSold: number
  totalRevenue: number
  occupancyRate?: number
}

export interface ShowtimeOccupancyResponse {
  showtimeId: string
  movieTitle: string
  cinemaName: string
  auditoriumName: string
  startTime: string
  format: string
  totalSeats: number
  occupiedSeats: number
  occupancyRate: number
}

