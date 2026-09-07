import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type {
  ShowtimeSummaryResponse,
  ShowtimeDetailResponse,
  ShowtimeSeatStatusResponse,
  ShowtimeQuery,
  CreateShowtimeRequest,
  UpdateShowtimeRequest,
  ShowtimeGenerationRequest,
  ShowtimeGenerationPreviewResponse,
  ShowtimeGenerationResultResponse,
  CopyScheduleRequest,
  CopyScheduleResultResponse,
  CalendarScheduleResponse,
  CinemaSchedulingConfigResponse,
  ValidateShowtimeSlotRequest,
  ValidateShowtimeSlotResponse,
  SuggestShowtimeSlotRequest,
  SuggestShowtimeSlotResponse,
  MoveShowtimeScheduleRequest,
} from '@/types/showtime.types'

export const showtimeService = {
  async getPublicShowtimes(query: ShowtimeQuery = {}): Promise<PageResponse<ShowtimeSummaryResponse>> {
    const params: Record<string, any> = {}

    if (query.movieId) {
      params.movieId = query.movieId
    }
    if (query.cinemaId) {
      params.cinemaId = query.cinemaId
    }
    if (query.auditoriumId) {
      params.auditoriumId = query.auditoriumId
    }
    if (query.date) {
      params.date = query.date
    }
    if (query.format) {
      if (query.format === '2D' || (query.format as string) === 'TWO_D') {
        params.format = 'TWO_D'
      } else if (query.format === '3D' || (query.format as string) === 'THREE_D') {
        params.format = 'THREE_D'
      } else {
        params.format = query.format
      }
    }
    if (query.language && query.language.trim()) {
      params.language = query.language.trim()
    }
    if (typeof query.page === 'number') {
      params.page = query.page
    }
    if (typeof query.size === 'number') {
      params.size = query.size
    }
    if (query.sort) {
      params.sort = query.sort
    }

    const response = await apiClient.get<PageResponse<ShowtimeSummaryResponse>>('/api/v1/showtimes', {
      params,
    })
    return response.data
  },

  async getPublicShowtimeDetail(id: string): Promise<ShowtimeDetailResponse> {
    const response = await apiClient.get<ShowtimeDetailResponse>(`/api/v1/showtimes/${id}`)
    return response.data
  },

  async getShowtimeSeats(id: string): Promise<ShowtimeSeatStatusResponse[]> {
    const response = await apiClient.get<ShowtimeSeatStatusResponse[]>(`/api/v1/showtimes/${id}/seats`)
    return response.data
  },

  // Admin Endpoints
  async getAdminShowtimes(params?: {
    movieId?: string
    cinemaId?: string
    auditoriumId?: string
    date?: string
    status?: string
    format?: string
    language?: string
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<ShowtimeSummaryResponse>> {
    const response = await apiClient.get<PageResponse<ShowtimeSummaryResponse>>('/api/v1/admin/showtimes', {
      params,
    })
    return response.data
  },

  async getAdminShowtimeDetail(id: string): Promise<ShowtimeDetailResponse> {
    const response = await apiClient.get<ShowtimeDetailResponse>(`/api/v1/admin/showtimes/${id}`)
    return response.data
  },

  async createShowtime(payload: CreateShowtimeRequest): Promise<ShowtimeDetailResponse> {
    const response = await apiClient.post<ShowtimeDetailResponse>('/api/v1/admin/showtimes', payload)
    return response.data
  },

  async updateShowtime(id: string, payload: UpdateShowtimeRequest): Promise<ShowtimeDetailResponse> {
    const response = await apiClient.put<ShowtimeDetailResponse>(`/api/v1/admin/showtimes/${id}`, payload)
    return response.data
  },

  async moveShowtimeSchedule(id: string, payload: MoveShowtimeScheduleRequest): Promise<ShowtimeDetailResponse> {
    const response = await apiClient.patch<ShowtimeDetailResponse>(`/api/v1/admin/showtimes/${id}/schedule`, payload)
    return response.data
  },

  async deleteShowtime(id: string): Promise<void> {
    await apiClient.delete(`/api/v1/admin/showtimes/${id}`)
  },

  async validateSingleSlot(payload: ValidateShowtimeSlotRequest): Promise<ValidateShowtimeSlotResponse> {
    const response = await apiClient.post<ValidateShowtimeSlotResponse>('/api/v1/admin/showtimes/validate', payload)
    return response.data
  },

  async suggestNextSlot(payload: SuggestShowtimeSlotRequest): Promise<SuggestShowtimeSlotResponse> {
    const response = await apiClient.post<SuggestShowtimeSlotResponse>(
      '/api/v1/admin/showtimes/suggest-next-slot',
      payload
    )
    return response.data
  },

  async previewGeneration(payload: ShowtimeGenerationRequest): Promise<ShowtimeGenerationPreviewResponse> {
    const response = await apiClient.post<ShowtimeGenerationPreviewResponse>(
      '/api/v1/admin/showtimes/generate/preview',
      payload
    )
    return response.data
  },

  async generateShowtimes(payload: ShowtimeGenerationRequest): Promise<ShowtimeGenerationResultResponse> {
    const response = await apiClient.post<ShowtimeGenerationResultResponse>(
      '/api/v1/admin/showtimes/generate',
      payload
    )
    return response.data
  },

  async copySchedule(payload: CopyScheduleRequest): Promise<CopyScheduleResultResponse> {
    const response = await apiClient.post<CopyScheduleResultResponse>('/api/v1/admin/showtimes/copy', payload)
    return response.data
  },

  async getCalendarSchedule(params: {
    cinemaId: string
    from?: string
    to?: string
  }): Promise<CalendarScheduleResponse> {
    const response = await apiClient.get<CalendarScheduleResponse>('/api/v1/admin/showtimes/calendar', { params })
    return response.data
  },

  async getCinemaSchedulingConfig(cinemaId: string): Promise<CinemaSchedulingConfigResponse> {
    const response = await apiClient.get<CinemaSchedulingConfigResponse>(
      '/api/v1/admin/showtimes/scheduling-config',
      {
        params: { cinemaId },
      }
    )
    return response.data
  },
}

export default showtimeService

