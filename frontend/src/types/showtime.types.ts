import type { MovieSummaryResponse } from './movie.types'
import type { CinemaSummaryResponse } from './cinema.types'

export type ShowtimeFormat = '2D' | '3D' | 'IMAX' | 'TWO_D' | 'THREE_D'
export type ShowtimeStatus = 'SCHEDULED' | 'CANCELLED' | 'FINISHED'
export type SeatAvailabilityStatus = 'AVAILABLE' | 'HELD' | 'SOLD' | 'BLOCKED'

export interface AuditoriumResponse {
  id: string
  cinemaId: string
  cinemaName?: string
  name: string
  type: string
  rowsCount?: number
  columnsCount?: number
  totalSeats?: number
  status?: string
}

export interface ShowtimeSummaryResponse {
  id: string
  movieId: string
  movieTitle: string
  moviePosterUrl?: string
  movieDurationMinutes?: number
  movieAgeRating?: string
  cinemaId: string
  cinemaName: string
  cinemaCity?: string
  auditoriumId: string
  auditoriumName: string
  auditoriumType?: string
  format: ShowtimeFormat
  language?: string
  subtitle?: string
  startTime: string // ISO LocalDateTime
  endTime: string // ISO LocalDateTime
  basePrice: number
  status: ShowtimeStatus
  createdAt: string
  updatedAt?: string
}

export interface ShowtimeDetailResponse {
  id: string
  movie: MovieSummaryResponse
  cinema: CinemaSummaryResponse
  auditorium: AuditoriumResponse
  format: ShowtimeFormat
  language?: string
  subtitle?: string
  startTime: string // ISO LocalDateTime
  endTime: string // ISO LocalDateTime
  basePrice: number
  status: ShowtimeStatus
  createdAt: string
  updatedAt?: string
}

export interface ShowtimeSeatStatusResponse {
  id: string
  auditoriumId: string
  seatTypeId: string
  seatTypeName: string
  priceModifier: number
  rowLabel: string
  seatNumber: number
  seatCode: string
  seatStatus: string
  availabilityStatus: SeatAvailabilityStatus
  isHeldByCurrentUser?: boolean
}


export interface ShowtimeQuery {
  movieId?: string
  cinemaId?: string
  auditoriumId?: string
  date?: string // YYYY-MM-DD
  format?: ShowtimeFormat
  language?: string
  page?: number
  size?: number
  sort?: string
}

// Client-side grouping structure for UI rendering
export interface FormatShowtimeGroup {
  format: ShowtimeFormat
  showtimes: ShowtimeSummaryResponse[]
}

export interface CinemaShowtimeGroup {
  cinemaId: string
  cinemaName: string
  cinemaCity?: string
  formats: FormatShowtimeGroup[]
}

export interface CreateShowtimeRequest {
  movieId: string
  auditoriumId: string
  format: string
  language: string
  subtitle?: string
  startTime: string
  endTime?: string
  basePrice: number
  status?: string
}

export interface UpdateShowtimeRequest {
  startTime?: string
  endTime?: string
  basePrice?: number
  format?: string
  language?: string
  subtitle?: string
  status?: string
}

export interface ShowtimeSlotPreviewResponse {
  date: string
  auditoriumId: string
  auditoriumName: string
  movieId: string
  movieTitle: string
  movieDurationMinutes: number
  startTime: string
  endTime: string
  format: ShowtimeFormat
  language: string
  subtitle?: string
  basePrice: number
  valid: boolean
  conflicts?: Array<{
    type: string
    message: string
    conflictingShowtimeId?: string
  }>
}

export interface ShowtimeGenerationPreviewResponse {
  totalProposed: number
  totalValid: number
  totalConflicted: number
  slots: ShowtimeSlotPreviewResponse[]
}

export interface ShowtimeGenerationResultResponse {
  totalGenerated: number
  totalSkipped: number
  showtimes: ShowtimeSummaryResponse[]
}

export interface ShowtimeGenerationRequest {
  movieId: string
  auditoriumIds: string[]
  startDate: string
  endDate?: string
  openingTime?: string
  closingTime?: string
  snapIntervalMinutes?: number
  staggerIntervalMinutes?: number
  format?: string
  language?: string
  subtitle?: string
  basePrice?: number
}

export interface CopyScheduleRequest {
  sourceDate: string
  targetDate: string
  cinemaId?: string
  auditoriumIds?: string[]
}

export interface CopyScheduleResultResponse {
  copiedCount: number
  skippedCount: number
  sourceDate: string
  targetDate: string
}

export interface CalendarScheduleResponse {
  cinemaId: string
  from: string
  to: string
  auditoriums: Array<{
    auditoriumId: string
    auditoriumName: string
    showtimes: ShowtimeSummaryResponse[]
  }>
}
