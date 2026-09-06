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
  paymentStatus: PaymentStatus
  status?: PaymentStatus
  createdAt: string
  paidAt?: string
  gatewayTransactionId?: string
}

export interface DemoPaymentCompleteRequest {
  paymentCode: string
  responseCode: '00' | '07' | '24'
}

export interface DemoPaymentCompleteResponse {
  paymentCode: string
  responseCode: string
  paymentStatus: PaymentStatus
  redirectUrl: string
  message: string
}
