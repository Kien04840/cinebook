export type PaymentMethod = 'VNPAY'
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'REFUNDED'

export interface InitiatePaymentRequest {
  paymentMethod: PaymentMethod
}

export interface InitiatePaymentResponse {
  paymentId: string
  paymentCode: string
  amount: number
  paymentUrl: string
  expiresAt: string
}

export interface PaymentResultResponse {
  paymentId: string
  bookingId: string
  bookingCode: string
  paymentCode: string
  amount: number
  paymentStatus: PaymentStatus
  responseCode: string
  message: string
}

export interface PaymentSummaryResponse {
  id: string
  paymentCode: string
  amount: number
  paymentMethod: PaymentMethod
  status: PaymentStatus
  createdAt: string
  paidAt?: string
  gatewayTransactionId?: string
}



