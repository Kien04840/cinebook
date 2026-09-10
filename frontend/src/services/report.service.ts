import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type {
  DashboardResponse,
  RevenueTrendResponse,
  MovieReportResponse,
  CinemaReportResponse,
  ShowtimeOccupancyResponse,
  UserStatisticsResponse,
  ReportType,
  ReportFormat,
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

  async exportReport(
    reportType: ReportType = 'REVENUE',
    format: ReportFormat = 'XLSX',
    from?: string,
    to?: string,
    extraParams?: {
      groupBy?: 'DAY' | 'MONTH' | 'YEAR'
      sortBy?: 'REVENUE' | 'TICKETS' | 'START_TIME' | 'OCCUPANCY_RATE'
      cinemaId?: string
      movieId?: string
      limit?: number
    }
  ): Promise<{ data: Blob; filename: string }> {
    const response = await apiClient.get<Blob>('/api/v1/admin/reports/export', {
      params: {
        reportType,
        format,
        from,
        to,
        ...extraParams,
      },
      responseType: 'blob',
    })

    let filename = ''
    const contentDisposition = response.headers?.['content-disposition'] || response.headers?.['Content-Disposition']
    if (contentDisposition) {
      const match = /filename\*?=['"]?(?:UTF-\d['"]*)?([^;\r\n"']*)['"]?/i.exec(contentDisposition)
      if (match && match[1]) {
        filename = decodeURIComponent(match[1].trim())
      }
    }

    if (!filename) {
      const ext = format.toLowerCase()
      const datePart = from && to ? `${from}_to_${to}` : (from || to || new Date().toISOString().split('T')[0])
      filename = `cinebook-${reportType.toLowerCase()}-report-${datePart}.${ext}`
    }

    return { data: response.data, filename }
  },

  downloadBlob(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', filename)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  },
}

export default reportService

