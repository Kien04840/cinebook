export type CinemaStatus = 'ACTIVE' | 'CLOSED'

export type AuditoriumStatus = 'ACTIVE' | 'MAINTENANCE' | 'DECOMMISSIONED'

export type SeatStatus = 'ACTIVE' | 'BROKEN'

export interface SeatResponse {
  id: string
  auditoriumId: string
  seatTypeId: string
  seatTypeName: string
  seatTypeCode?: string
  capacity?: number
  colorToken?: string
  icon?: string
  rowLabel: string
  seatNumber: number
  seatCode: string
  status: SeatStatus
  priceModifier?: number
}

export interface AuditoriumResponse {
  id: string
  cinemaId: string
  cinemaName?: string
  name: string
  type: string
  rowsCount: number
  columnsCount: number
  totalSeats: number
  status: AuditoriumStatus
  turnaroundMinutes?: number
  snapIntervalMinutes?: number
  createdAt?: string
  updatedAt?: string
}

export interface AuditoriumDetailResponse extends AuditoriumResponse {
  seats?: SeatResponse[]
  hasShowtimes?: boolean
  hasBookings?: boolean
  hasTickets?: boolean
  canModifyLayout?: boolean
  canModifySeatTypes?: boolean
}

export interface NormalizeEmptyLayoutsResponse {
  processedCount: number
  updatedCount: number
  skippedCount: number
  scannedCount?: number
  normalizedCount?: number
  unchangedCount?: number
  skippedBecauseBookings?: number
  skippedBecauseTickets?: number
  skippedBecauseActiveHolds?: number
  failedCount?: number
  updatedAuditoriumIds: string[]
  skippedDetails: {
    auditoriumId: string
    auditoriumName: string
    cinemaName: string
    reason: string
  }[]
}

export interface BatchUpdateSeatTypePreviewResponse {
  seatCount: number
  isProtected: boolean
  targetSeatTypeName: string
  willDeleteSeatCodes: string[]
  isValid: boolean
  validationError?: string
}

export interface BatchUpdateSeatStatusRequest {
  seatIds: string[]
  status: SeatStatus
}

export interface CinemaSummaryResponse {
  id: string
  name: string
  address: string
  city: string
  status: CinemaStatus
  openingTime: string
  closingTime: string
  auditoriumsCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface CinemaDetailResponse {
  id: string
  name: string
  address: string
  city: string
  status: CinemaStatus
  openingTime: string
  closingTime: string
  auditoriums?: AuditoriumResponse[]
  createdAt?: string
  updatedAt?: string
}

export interface CreateCinemaRequest {
  name: string
  address: string
  city: string
  status?: CinemaStatus
  openingTime: string
  closingTime: string
}

export interface UpdateCinemaRequest {
  name: string
  address: string
  city: string
  status?: CinemaStatus
  openingTime: string
  closingTime: string
}

export interface CreateAuditoriumRequest {
  name: string
  type: string
  rowsCount: number
  columnsCount: number
  status?: AuditoriumStatus
  turnaroundMinutes?: number
  snapIntervalMinutes?: number
  defaultSeatTypeId?: string
}

export interface UpdateAuditoriumRequest {
  name: string
  type: string
  status: AuditoriumStatus
  turnaroundMinutes?: number
  snapIntervalMinutes?: number
}

export interface BatchUpdateSeatTypeRequest {
  seatIds: string[]
  seatTypeId: string
}

export interface UpdateSeatStatusRequest {
  status: SeatStatus
}

