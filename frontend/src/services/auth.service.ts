import apiClient from './api'
import type { MessageResponse } from '@/types/api.types'
import type { AuthResponse, LoginPayload, RegisterPayload } from '@/types/auth.types'

export const authService = {
  async login(payload: LoginPayload): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/api/v1/auth/login', payload)
    return response.data
  },

  async register(payload: RegisterPayload): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/api/v1/auth/register', payload)
    return response.data
  },

  async refreshToken(refreshToken: string): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/api/v1/auth/refresh', {
      refreshToken,
    })
    return response.data
  },

  async logout(refreshToken?: string): Promise<void> {
    try {
      await apiClient.post('/api/v1/auth/logout', refreshToken ? { refreshToken } : undefined)
    } catch {
      // Gracefully ignore server logout errors on client-side cleanup
    }
  },

  async requestPasswordReset(email: string): Promise<MessageResponse> {
    const response = await apiClient.post<MessageResponse>('/api/v1/auth/password-reset/request', {
      email,
    })
    return response.data
  },
}

export default authService
