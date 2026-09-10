export interface GenreResponse {
  id: string
  name: string
  description?: string
}

export interface CreateGenreRequest {
  name: string
  description?: string
}

export interface UpdateGenreRequest {
  name: string
  description?: string
}

