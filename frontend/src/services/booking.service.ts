import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type {
  BookingDetailResponse,
  BookingSummaryResponse,
  CreateBookingPayload,
  CancelBookingPayload,
} from '@/types/booking.types'

export const bookingService = {
  async createBooking(payload: CreateBookingPayload): Promise<BookingDetailResponse> {
    const response = await apiClient.post<BookingDetailResponse>('/api/v1/bookings', payload)
    return response.data
  },

  async getBookingDetail(id: string): Promise<BookingDetailResponse> {
    const response = await apiClient.get<BookingDetailResponse>(`/api/v1/bookings/${id}`)
    return response.data
  },

  async getActiveBooking(showtimeId: string): Promise<BookingDetailResponse | null> {
    const response = await apiClient.get<BookingDetailResponse | null>('/api/v1/bookings/active', {
      params: { showtimeId },
    })
    return response.data
  },


  async getMyBookings(params?: {
    status?: string
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<BookingSummaryResponse>> {
    const response = await apiClient.get<PageResponse<BookingSummaryResponse>>('/api/v1/bookings/me', {
      params,
    })
    return response.data
  },

  async cancelBooking(id: string, payload?: CancelBookingPayload): Promise<BookingDetailResponse> {
    const response = await apiClient.post<BookingDetailResponse>(`/api/v1/bookings/${id}/cancel`, payload || {})
    return response.data
  },

  async getAdminBookings(params?: {
    q?: string
    status?: string
    showtimeId?: string
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<BookingSummaryResponse>> {
    const response = await apiClient.get<PageResponse<BookingSummaryResponse>>('/api/v1/admin/bookings', {
      params,
    })
    return response.data
  },

  async cancelAdminBooking(id: string, payload?: CancelBookingPayload): Promise<BookingDetailResponse> {
    const response = await apiClient.post<BookingDetailResponse>(`/api/v1/admin/bookings/${id}/cancel`, payload || {})
    return response.data
  },
}

export default bookingService

