import apiClient from './api'
import type { MessageResponse } from '@/types/api.types'
import type { UserProfileResponse, UpdateProfilePayload, ChangePasswordPayload } from '@/types/auth.types'

export const userService = {
  async getProfile(): Promise<UserProfileResponse> {
    const response = await apiClient.get<UserProfileResponse>('/api/v1/users/me')
    return response.data
  },

  async updateProfile(payload: UpdateProfilePayload): Promise<UserProfileResponse> {
    const response = await apiClient.put<UserProfileResponse>('/api/v1/users/me', payload)
    return response.data
  },

  async changePassword(payload: ChangePasswordPayload): Promise<MessageResponse> {
    const response = await apiClient.patch<MessageResponse>('/api/v1/users/me/password', payload)
    return response.data
  },

  async getAdminUsers(params?: {
    q?: string
    status?: string
    page?: number
    size?: number
    sort?: string
  }): Promise<{ content: UserProfileResponse[]; totalElements: number; totalPages: number; pageNumber: number; pageSize: number }> {
    const response = await apiClient.get('/api/v1/admin/users', { params })
    return response.data
  },

  async updateUserStatus(userId: string, status: string): Promise<UserProfileResponse> {
    const response = await apiClient.patch<UserProfileResponse>(`/api/v1/admin/users/${userId}/status`, null, {
      params: { status },
    })
    return response.data
  },
}

export default userService

