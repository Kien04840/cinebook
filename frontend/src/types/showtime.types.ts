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
  seatTypeCode?: string
  capacity?: number
  colorToken?: string
  icon?: string
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
  status?: ShowtimeStatus
}

export interface UpdateShowtimeRequest {
  movieId: string
  auditoriumId: string
  format: string
  language: string
  subtitle?: string
  startTime: string
  endTime?: string
  basePrice: number
  status: ShowtimeStatus
}

export type SchedulingConflictType =
  | 'SHOWTIME_OVERLAP'
  | 'TURNAROUND_VIOLATION'
  | 'OUTSIDE_OPERATING_HOURS'
  | 'AUDITORIUM_INACTIVE'
  | 'AUDITORIUM_MAINTENANCE'
  | 'AUDITORIUM_DECOMMISSIONED'
  | 'CINEMA_INACTIVE'
  | 'MOVIE_NOT_AVAILABLE'
  | 'INVALID_TIME'
  | 'ALREADY_EXISTS'

export interface SchedulingConflictResponse {
  type: SchedulingConflictType
  auditoriumId?: string
  auditoriumName?: string
  existingShowtimeId?: string
  conflictingStartTime?: string
  conflictingEndTime?: string
  message?: string
}

export interface AuditoriumSchedulingConfigResponse {
  id: string
  name: string
  type: string
  status: string
  turnaroundMinutes: number
  snapIntervalMinutes: number
}

export interface CinemaSchedulingConfigResponse {
  cinemaId: string
  cinemaName: string
  openingTime: string
  closingTime: string
  auditoriums: AuditoriumSchedulingConfigResponse[]
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
  conflicts: SchedulingConflictResponse[]
}

export interface MovieGenerationConfig {
  movieId: string
  targetScreeningsPerDay: number
  format?: ShowtimeFormat
  language?: string
  subtitle?: string
  basePrice?: number
}

export interface MovieGenerationSummary {
  movieId: string
  movieTitle: string
  targetScreenings: number
  scheduledScreenings: number
  remainingScreenings: number
}

export interface MoveShowtimeScheduleRequest {
  auditoriumId?: string
  startTime: string
}

export interface ShowtimeGenerationPreviewResponse {
  totalProposed: number
  totalValid: number
  totalConflicted: number
  totalRequested?: number
  totalScheduled?: number
  totalUnscheduled?: number
  movieSummaries?: MovieGenerationSummary[]
  warnings?: string[]
  qualityIndicators?: string[]
  slots: ShowtimeSlotPreviewResponse[]
}

export interface ShowtimeGenerationResultResponse {
  totalCreated: number
  totalSkipped: number
  totalConflicted: number
  totalRequested?: number
  totalScheduled?: number
  totalUnscheduled?: number
  movieSummaries?: MovieGenerationSummary[]
  warnings?: string[]
  createdShowtimes: ShowtimeSummaryResponse[]
  conflicts: SchedulingConflictResponse[]
}

export interface ShowtimeGenerationRequest {
  movies?: MovieGenerationConfig[]
  movieId?: string
  auditoriumIds: string[]
  startDate: string
  endDate?: string
  openingTime?: string
  closingTime?: string
  snapIntervalMinutes?: number
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
  totalCopied: number
  totalSkipped: number
  totalConflicted: number
  createdShowtimes: ShowtimeSummaryResponse[]
  conflicts: SchedulingConflictResponse[]
}

export interface CalendarAuditoriumShowtimesResponse {
  auditoriumId: string
  auditoriumName: string
  auditoriumType: string
  showtimes: ShowtimeSummaryResponse[]
}

export interface CalendarScheduleResponse {
  cinemaId: string
  cinemaName: string
  from: string
  to: string
  auditoriums: CalendarAuditoriumShowtimesResponse[]
}

export interface ValidateShowtimeSlotRequest {
  movieId: string
  auditoriumId: string
  startTime: string
  format?: string
  language?: string
  subtitle?: string
  excludeShowtimeId?: string
}

export interface ValidateShowtimeSlotResponse {
  valid: boolean
  calculatedStartTime?: string
  calculatedEndTime?: string
  movieDurationMinutes?: number
  occupancyEndTime?: string
  conflicts: SchedulingConflictResponse[]
}

export interface SuggestShowtimeSlotRequest {
  movieId: string
  auditoriumId: string
  requestedStartTime: string
  snapIntervalMinutes?: number
}

export interface SuggestShowtimeSlotResponse {
  available: boolean
  suggestedStartTime?: string
  suggestedEndTime?: string
  movieDurationMinutes?: number
  occupancyEndTime?: string
  message?: string
}
