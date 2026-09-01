import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type {
  DashboardResponse,
  RevenueTrendResponse,
  MovieReportResponse,
  CinemaReportResponse,
  ShowtimeOccupancyResponse,
  UserStatisticsResponse,
} from '@/types/report.types'

export const reportService = {
  async getDashboardSummary(from?: string, to?: string): Promise<DashboardResponse> {
    const response = await apiClient.get<DashboardResponse>('/api/v1/admin/reports/dashboard', {
      params: { from, to },
    })
    return response.data
  },

  async getUserStatistics(from?: string, to?: string): Promise<UserStatisticsResponse> {
    const response = await apiClient.get<UserStatisticsResponse>('/api/v1/admin/reports/users', {
      params: { from, to },
    })
    return response.data
  },

  async getRevenueTrend(
    from?: string,
    to?: string,
    groupBy: 'DAY' | 'MONTH' | 'YEAR' = 'DAY'
  ): Promise<RevenueTrendResponse[]> {
    const response = await apiClient.get<RevenueTrendResponse[]>('/api/v1/admin/reports/revenue', {
      params: { from, to, groupBy },
    })
    return response.data
  },

  async getMovieReport(
    from?: string,
    to?: string,
    sortBy: 'REVENUE' | 'TICKETS' = 'REVENUE',
    limit?: number
  ): Promise<MovieReportResponse[]> {
    const response = await apiClient.get<MovieReportResponse[]>('/api/v1/admin/reports/movies', {
      params: { from, to, sortBy, limit },
    })
    return response.data
  },

  async getCinemaReport(
    from?: string,
    to?: string,
    sortBy: 'REVENUE' | 'TICKETS' = 'REVENUE',
    limit?: number
  ): Promise<CinemaReportResponse[]> {
    const response = await apiClient.get<CinemaReportResponse[]>('/api/v1/admin/reports/cinemas', {
      params: { from, to, sortBy, limit },
    })
    return response.data
  },

  async getShowtimeOccupancy(params?: {
    from?: string
    to?: string
    cinemaId?: string
    movieId?: string
    page?: number
    size?: number
  }): Promise<PageResponse<ShowtimeOccupancyResponse>> {
    const response = await apiClient.get<PageResponse<ShowtimeOccupancyResponse>>(
      '/api/v1/admin/reports/showtimes/occupancy',
      { params }
    )
    return response.data
  },

  async exportReport(type: string, format: 'XLSX' | 'CSV' = 'XLSX', from?: string, to?: string): Promise<Blob> {
    const response = await apiClient.get('/api/v1/admin/reports/export', {
      params: { type, format, from, to },
      responseType: 'blob',
    })
    return response.data
  },
}

export default reportService

