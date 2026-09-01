import type { GenreResponse } from './genre.types'

export type MovieStatus = 'COMING_SOON' | 'NOW_SHOWING' | 'ENDED' | 'HIDDEN'

export interface MovieSummaryResponse {
  id: string
  tmdbId?: number
  title: string
  originalTitle?: string
  posterUrl?: string
  backdropUrl?: string
  durationMinutes?: number
  releaseDate?: string // YYYY-MM-DD
  ageRating?: string
  status: MovieStatus
  genres: GenreResponse[]
}

export interface MovieDetailResponse {
  id: string
  tmdbId?: number
  title: string
  originalTitle?: string
  overview?: string
  durationMinutes?: number
  director?: string
  actors?: string
  country?: string
  language?: string
  releaseDate?: string // YYYY-MM-DD
  ageRating?: string
  posterUrl?: string
  backdropUrl?: string
  trailerUrl?: string
  status: MovieStatus
  genres: GenreResponse[]
  createdAt: string
  updatedAt?: string
}

export interface MovieQuery {
  q?: string
  genre?: string
  status?: MovieStatus
  page?: number
  size?: number
  sort?: string
}

export interface MovieRecommendationResponse {
  explanation: string
  favoriteGenres: string[]
  movies: MovieSummaryResponse[]
}


