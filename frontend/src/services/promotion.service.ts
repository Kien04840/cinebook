import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type {
  PromotionResponse,
  ValidatePromotionResponse,
  CreatePromotionRequest,
  UpdatePromotionRequest,
  UpdatePromotionStatusRequest,
  PromotionStatus,
} from '@/types/promotion.types'

export const promotionService = {
  // Public / Customer validation preview
  async getPublicPromotions(params?: {
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<PromotionResponse>> {
    const response = await apiClient.get<PageResponse<PromotionResponse>>('/api/v1/promotions', {
      params,
    })
    return response.data
  },

  async validatePromotionCode(
    code: string,
    grossAmount: number
  ): Promise<ValidatePromotionResponse> {
    const response = await apiClient.get<ValidatePromotionResponse>('/api/v1/promotions/validate', {
      params: { code, grossAmount },
    })
    return response.data
  },

  // Admin Management Endpoints
  async getAdminPromotions(params?: {
    status?: PromotionStatus
    q?: string
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<PromotionResponse>> {
    const response = await apiClient.get<PageResponse<PromotionResponse>>('/api/v1/admin/promotions', {
      params,
    })
    return response.data
  },

  async getPromotionDetail(id: string): Promise<PromotionResponse> {
    const response = await apiClient.get<PromotionResponse>(`/api/v1/admin/promotions/${id}`)
    return response.data
  },

  async createPromotion(payload: CreatePromotionRequest): Promise<PromotionResponse> {
    const response = await apiClient.post<PromotionResponse>('/api/v1/admin/promotions', payload)
    return response.data
  },

  async updatePromotion(id: string, payload: UpdatePromotionRequest): Promise<PromotionResponse> {
    const response = await apiClient.put<PromotionResponse>(`/api/v1/admin/promotions/${id}`, payload)
    return response.data
  },

  async updatePromotionStatus(
    id: string,
    payload: UpdatePromotionStatusRequest
  ): Promise<PromotionResponse> {
    const response = await apiClient.patch<PromotionResponse>(
      `/api/v1/admin/promotions/${id}/status`,
      payload
    )
    return response.data
  },
}

export default promotionService

