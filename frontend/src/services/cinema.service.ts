import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type {
  CinemaSummaryResponse,
  CinemaDetailResponse,
  AuditoriumResponse,
  CreateCinemaRequest,
  UpdateCinemaRequest,
  CreateAuditoriumRequest,
} from '@/types/cinema.types'

export const cinemaService = {
  // Public
  async getPublicCinemas(params?: {
    city?: string
    status?: string
    q?: string
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<CinemaSummaryResponse>> {
    const response = await apiClient.get<PageResponse<CinemaSummaryResponse>>('/api/v1/cinemas', {
      params,
    })
    return response.data
  },

  async getPublicCinemaDetail(id: string): Promise<CinemaDetailResponse> {
    const response = await apiClient.get<CinemaDetailResponse>(`/api/v1/cinemas/${id}`)
    return response.data
  },

  async getAuditoriumsByCinema(cinemaId: string): Promise<AuditoriumResponse[]> {
    const response = await apiClient.get<AuditoriumResponse[]>(`/api/v1/cinemas/${cinemaId}/auditoriums`)
    return response.data
  },

  // Admin
  async getAdminCinemas(params?: {
    city?: string
    status?: string
    q?: string
    includeDeleted?: boolean
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<CinemaSummaryResponse>> {
    const response = await apiClient.get<PageResponse<CinemaSummaryResponse>>('/api/v1/admin/cinemas', {
      params,
    })
    return response.data
  },

  async getAdminCinemaDetail(id: string): Promise<CinemaDetailResponse> {
    const response = await apiClient.get<CinemaDetailResponse>(`/api/v1/admin/cinemas/${id}`)
    return response.data
  },

  async createCinema(payload: CreateCinemaRequest): Promise<CinemaDetailResponse> {
    const response = await apiClient.post<CinemaDetailResponse>('/api/v1/admin/cinemas', payload)
    return response.data
  },

  async updateCinema(id: string, payload: UpdateCinemaRequest): Promise<CinemaDetailResponse> {
    const response = await apiClient.put<CinemaDetailResponse>(`/api/v1/admin/cinemas/${id}`, payload)
    return response.data
  },

  async deleteCinema(id: string): Promise<void> {
    await apiClient.delete(`/api/v1/admin/cinemas/${id}`)
  },

  async createAuditorium(cinemaId: string, payload: CreateAuditoriumRequest): Promise<any> {
    const response = await apiClient.post(`/api/v1/admin/cinemas/${cinemaId}/auditoriums`, payload)
    return response.data
  },
}

export default cinemaService

