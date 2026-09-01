export type RefundStatus = 'PENDING' | 'SUCCESS' | 'FAILED'

export interface RefundRequest {
  reason?: string
}

export interface RefundResponse {
  id: string
  paymentId: string
  bookingId: string
  bookingCode: string
  refundCode: string
  gatewayRefundId?: string
  amount: number
  refundReason?: string
  refundStatus: RefundStatus
  processedAt?: string
  createdAt: string
}

export interface RefundStatisticsResponse {
  totalRefunds: number
  successfulRefunds: number
  failedRefunds: number
  totalRefundedAmount: number
}

