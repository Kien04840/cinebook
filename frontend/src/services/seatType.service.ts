import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type {
  SeatTypeResponse,
  CreateSeatTypeRequest,
  UpdateSeatTypeRequest,
} from '@/types/seatType.types'

export const seatTypeService = {
  async getAdminSeatTypes(params?: {
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<SeatTypeResponse>> {
    const response = await apiClient.get<PageResponse<SeatTypeResponse>>('/api/v1/admin/seat-types', {
      params,
    })
    return response.data
  },

  async getSeatTypeDetail(id: string): Promise<SeatTypeResponse> {
    const response = await apiClient.get<SeatTypeResponse>(`/api/v1/admin/seat-types/${id}`)
    return response.data
  },

  async createSeatType(payload: CreateSeatTypeRequest): Promise<SeatTypeResponse> {
    const response = await apiClient.post<SeatTypeResponse>('/api/v1/admin/seat-types', payload)
    return response.data
  },

  async updateSeatType(id: string, payload: UpdateSeatTypeRequest): Promise<SeatTypeResponse> {
    const response = await apiClient.put<SeatTypeResponse>(`/api/v1/admin/seat-types/${id}`, payload)
    return response.data
  },
}

export default seatTypeService

