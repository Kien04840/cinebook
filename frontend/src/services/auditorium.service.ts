import apiClient from './api'
import type {
  AuditoriumResponse,
  AuditoriumDetailResponse,
  CreateAuditoriumRequest,
  UpdateAuditoriumRequest,
  SeatResponse,
  BatchUpdateSeatTypeRequest,
  UpdateSeatStatusRequest,
} from '@/types/cinema.types'

export const auditoriumService = {
  async getAuditoriumsByCinema(cinemaId: string): Promise<AuditoriumResponse[]> {
    const response = await apiClient.get<AuditoriumResponse[]>(`/api/v1/admin/cinemas/${cinemaId}/auditoriums`)
    return response.data
  },

  async getAuditoriumDetail(id: string): Promise<AuditoriumDetailResponse> {
    const response = await apiClient.get<AuditoriumDetailResponse>(`/api/v1/admin/auditoriums/${id}`)
    return response.data
  },

  async createAuditorium(cinemaId: string, payload: CreateAuditoriumRequest): Promise<AuditoriumDetailResponse> {
    const response = await apiClient.post<AuditoriumDetailResponse>(`/api/v1/admin/cinemas/${cinemaId}/auditoriums`, payload)
    return response.data
  },

  async updateAuditorium(id: string, payload: UpdateAuditoriumRequest): Promise<AuditoriumResponse> {
    const response = await apiClient.put<AuditoriumResponse>(`/api/v1/admin/auditoriums/${id}`, payload)
    return response.data
  },

  async deleteAuditorium(id: string): Promise<void> {
    await apiClient.delete(`/api/v1/admin/auditoriums/${id}`)
  },

  async getAuditoriumSeats(id: string): Promise<SeatResponse[]> {
    const response = await apiClient.get<SeatResponse[]>(`/api/v1/admin/auditoriums/${id}/seats`)
    return response.data
  },

  async updateSeatType(auditoriumId: string, seatId: string, seatTypeId: string): Promise<SeatResponse> {
    const response = await apiClient.put<SeatResponse>(`/api/v1/admin/auditoriums/${auditoriumId}/seats/${seatId}/seat-type`, {
      seatTypeId,
    })
    return response.data
  },

  async batchUpdateSeatType(auditoriumId: string, payload: BatchUpdateSeatTypeRequest): Promise<SeatResponse[]> {
    const response = await apiClient.put<SeatResponse[]>(`/api/v1/admin/auditoriums/${auditoriumId}/seats/batch-seat-type`, payload)
    return response.data
  },

  async updateSeatStatus(auditoriumId: string, seatId: string, payload: UpdateSeatStatusRequest): Promise<SeatResponse> {
    const response = await apiClient.patch<SeatResponse>(`/api/v1/admin/auditoriums/${auditoriumId}/seats/${seatId}/status`, payload)
    return response.data
  },
}

export default auditoriumService