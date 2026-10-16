export type NotificationType =
  | 'BOOKING_SUCCESS'
  | 'PAYMENT_SUCCESS'
  | 'PAYMENT_FAILED'
  | 'BOOKING_CANCELLED'
  | 'REFUND_SUCCESS'
  | 'REFUND_COMPLETED'
  | 'UPCOMING_SHOWTIME'

export interface NotificationResponse {
  id: string
  title: string
  message: string
  type: NotificationType
  bookingId?: string | null
  bookingCode?: string | null
  referenceType?: string | null
  referenceId?: string | null
  isRead: boolean
  createdAt: string
  readAt?: string | null
}

export interface UnreadCountResponse {
  unreadCount: number
}

