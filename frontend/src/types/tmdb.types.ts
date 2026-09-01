export interface TmdbGenreSyncResponse {
  created: number
  updated: number
  unchanged: number
  total: number
}

export interface TmdbMovieImportResponse {
  movieId: string
  tmdbId: number
  title: string
  originalTitle?: string
  action: 'CREATED' | 'UPDATED' | string
  status: string
  releaseDate: string
  ageRating: string
  genres: string[]
  posterUrl?: string
  trailerUrl?: string
}