export type NotificationType = 'PAYMENT_SUCCESS' | 'BOOKING_CANCELLED' | 'REFUND_COMPLETED'

export interface NotificationResponse {
  id: string
  title: string
  message: string
  type: NotificationType
  bookingId: string
  bookingCode?: string | null
  isRead: boolean
  createdAt: string
  readAt?: string | null
}

export interface UnreadCountResponse {
  unreadCount: number
}

