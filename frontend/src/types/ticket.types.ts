export type TicketStatus = 'VALID' | 'USED' | 'CANCELLED'

export interface TicketVerifyResponse {
  ticketId: string
  qrCode: string
  ticketPrice: number
  ticketStatus: TicketStatus
  bookingId: string | null
  bookingCode: string | null
  customerName: string | null
  customerEmail: string | null
  movieTitle: string | null
  moviePosterUrl: string | null
  cinemaName: string | null
  auditoriumName: string | null
  startTime: string | null
  endTime: string | null
  rowLabel: string | null
  seatNumber: number | null
  seatCode: string | null
  seatTypeName: string | null
  checkInEligible: boolean
  ineligibleReason: string | null
}

export interface TicketCheckInResponse {
  ticketId: string
  qrCode: string
  ticketStatus: TicketStatus
  checkedInAt: string
  message: string
  bookingCode: string | null
  seatCode: string | null
  movieTitle: string | null
  auditoriumName: string | null
  startTime: string | null
}

