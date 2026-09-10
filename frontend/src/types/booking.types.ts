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
  paymentStatus: string
  status?: string
  createdAt: string
  paidAt?: string
  gatewayTransactionId?: string
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
  checkInCode?: string
  bookingStatus: BookingStatus
  totalAmount: number
  grossAmount: number
  discountAmount: number
  foodAmount?: number
  holdExpiresAt: string // ISO string
  createdAt: string
  cancelledAt?: string
  cancelledReason?: string
  showtime: ShowtimeDetailResponse
  seats: BookingSeatResponse[]
  tickets?: TicketResponse[]
  payments?: PaymentSummaryResponse[]
  promotion?: BookingPromotionResponse
  foods?: import('./food.types').BookingFoodResponse[]
  user?: UserSummaryResponse
}

export interface BookingTicketItemResponse {
  ticketId: string
  seatCode: string
  rowLabel: string
  seatNumber: number
  seatTypeName: string
  ticketPrice: number
  ticketStatus: string
}

export interface BookingVerifyResponse {
  bookingId: string
  bookingCode: string
  checkInCode: string
  bookingStatus: BookingStatus
  customerName?: string
  customerEmail?: string
  customerPhone?: string
  movieTitle?: string
  moviePosterUrl?: string
  cinemaName?: string
  auditoriumName?: string
  startTime?: string
  endTime?: string
  tickets: BookingTicketItemResponse[]
  totalTickets: number
  validTickets: number
  usedTickets: number
  checkInEligible: boolean
  ineligibleReason?: string
}

export interface BookingCheckInResponse {
  bookingId: string
  bookingCode: string
  checkInCode: string
  result: string
  checkedInAt: string
  message: string
  movieTitle?: string
  cinemaName?: string
  auditoriumName?: string
  startTime?: string
  tickets: BookingTicketItemResponse[]
  totalTickets: number
  checkedInCount: number
  alreadyUsedCount: number
}

export interface BookingSummaryResponse {
  id: string
  bookingCode: string
  bookingStatus: BookingStatus
  totalAmount: number
  hasUsedTickets?: boolean
  seatCount?: number
  seatsCount?: number
  seatCodes?: string[]
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
  foodItems?: import('./food.types').BookingFoodItemRequest[]
}

export interface CancelBookingPayload {
  reason?: string
}

export type { BookingFoodResponse, BookingFoodItemRequest } from './food.types'

