import apiClient from './api'
import type { TmdbGenreSyncResponse, TmdbMovieImportResponse } from '@/types/tmdb.types'

export const tmdbService = {
  async syncGenres(): Promise<TmdbGenreSyncResponse> {
    const response = await apiClient.post<TmdbGenreSyncResponse>('/api/v1/admin/tmdb/genres/sync')
    return response.data
  },

  async importMovie(tmdbId: number): Promise<TmdbMovieImportResponse> {
    const response = await apiClient.post<TmdbMovieImportResponse>(`/api/v1/admin/tmdb/movies/${tmdbId}/import`)
    return response.data
  },
}

export default tmdbService