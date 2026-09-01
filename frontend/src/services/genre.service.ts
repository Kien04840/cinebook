import apiClient from './api'
import type { GenreResponse } from '@/types/genre.types'

let cachedGenresPromise: Promise<GenreResponse[]> | null = null

export const genreService = {
  async getAllGenres(forceRefresh = false): Promise<GenreResponse[]> {
    if (!cachedGenresPromise || forceRefresh) {
      cachedGenresPromise = apiClient
        .get<GenreResponse[]>('/api/v1/genres')
        .then((response) => response.data)
        .catch((err) => {
          cachedGenresPromise = null
          throw err
        })
    }
    return cachedGenresPromise
  },
}

export default genreService

