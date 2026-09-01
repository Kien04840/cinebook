import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type {
  InitiatePaymentRequest,
  InitiatePaymentResponse,
  PaymentResultResponse,
  PaymentSummaryResponse,
} from '@/types/payment.types'
import type {
  RefundRequest,
  RefundResponse,
  RefundStatus,
} from '@/types/refund.types'

export const paymentService = {
  // Payment initiation & verification
  async initiatePayment(
    bookingId: string,
    payload: InitiatePaymentRequest = { paymentMethod: 'VNPAY' }
  ): Promise<InitiatePaymentResponse> {
    const response = await apiClient.post<InitiatePaymentResponse>(
      `/api/v1/bookings/${bookingId}/payments`,
      payload
    )
    return response.data
  },

  async processReturn(params: Record<string, any>): Promise<PaymentResultResponse> {
    const response = await apiClient.get<PaymentResultResponse>('/api/v1/payments/vnpay/return', {
      params,
    })
    return response.data
  },

  async getPaymentDetail(paymentId: string): Promise<PaymentSummaryResponse> {
    const response = await apiClient.get<PaymentSummaryResponse>(`/api/v1/payments/${paymentId}`)
    return response.data
  },

  // Customer Refund Endpoints
  async refundPayment(paymentId: string, payload: RefundRequest = {}): Promise<RefundResponse> {
    const response = await apiClient.post<RefundResponse>(
      `/api/v1/payments/${paymentId}/refund`,
      payload
    )
    return response.data
  },

  async getRefundDetail(paymentId: string): Promise<RefundResponse> {
    const response = await apiClient.get<RefundResponse>(`/api/v1/payments/${paymentId}/refund`)
    return response.data
  },

  // Admin Refund Endpoints
  async adminRefundBooking(
    bookingId: string,
    payload: RefundRequest = {}
  ): Promise<RefundResponse> {
    const response = await apiClient.post<RefundResponse>(
      `/api/v1/admin/bookings/${bookingId}/refund`,
      payload
    )
    return response.data
  },

  async getAdminRefunds(params?: {
    status?: RefundStatus
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<RefundResponse>> {
    const response = await apiClient.get<PageResponse<RefundResponse>>('/api/v1/admin/refunds', {
      params,
    })
    return response.data
  },
}

export default paymentService
