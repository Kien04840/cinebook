import type { ShowtimeDetailResponse, ShowtimeSummaryResponse } from './showtime.types'

export type BookingStatus = 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'REFUNDED' | 'EXPIRED'

export interface BookingSeatResponse {
  seatId: string
  rowLabel: string
  seatNumber: number
  seatCode: string
  seatTypeId: string
  seatTypeName: string
  price: number
}

export interface TicketResponse {
  id: string
  seatId?: string
  seatCode: string
  ticketPrice: number
  ticketStatus: string
  qrCode: string
  createdAt?: string
}

export interface PaymentSummaryResponse {
  id: string
  paymentCode: string
  amount: number
  paymentMethod: string
  status: string
  createdAt: string
}

export interface BookingPromotionResponse {
  code: string
  discountType: string
  discountValue: number
  discountAmount: number
}

export interface UserSummaryResponse {
  id: string
  email: string
  fullName: string
  phone?: string
  avatarUrl?: string
}


export interface BookingDetailResponse {
  id: string
  bookingCode: string
  bookingStatus: BookingStatus
  totalAmount: number
  grossAmount: number
  discountAmount: number
  holdExpiresAt: string // ISO string
  createdAt: string
  cancelledAt?: string
  cancelledReason?: string
  showtime: ShowtimeDetailResponse
  seats: BookingSeatResponse[]
  tickets?: TicketResponse[]
  payments?: PaymentSummaryResponse[]
  promotion?: BookingPromotionResponse
  user?: UserSummaryResponse
}

export interface BookingSummaryResponse {
  id: string
  bookingCode: string
  bookingStatus: BookingStatus
  totalAmount: number
  seatCount?: number
  seatsCount?: number
  holdExpiresAt?: string
  createdAt: string
  showtime?: ShowtimeSummaryResponse
  movieTitle?: string
  cinemaName?: string
  showtimeStartTime?: string
  user?: UserSummaryResponse
}

export interface CreateBookingPayload {
  showtimeId: string
  seatIds: string[]
  promotionCode?: string
}

export interface CancelBookingPayload {
  reason?: string
}

