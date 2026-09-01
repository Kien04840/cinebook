import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type { MovieSummaryResponse, MovieDetailResponse, MovieQuery, MovieRecommendationResponse } from '@/types/movie.types'

export const movieService = {
  async getPublicMovies(query: MovieQuery = {}): Promise<PageResponse<MovieSummaryResponse>> {
    const params: Record<string, any> = {}

    if (query.q && query.q.trim()) {
      params.q = query.q.trim()
    }
    if (query.genre && query.genre.trim()) {
      params.genre = query.genre.trim()
    }
    if (query.status) {
      params.status = query.status
    }
    if (typeof query.page === 'number') {
      params.page = query.page
    }
    if (typeof query.size === 'number') {
      params.size = query.size
    }
    if (query.sort) {
      params.sort = query.sort
    }

    const response = await apiClient.get<PageResponse<MovieSummaryResponse>>('/api/v1/movies', {
      params,
    })
    return response.data
  },

  async getMovieDetail(id: string): Promise<MovieDetailResponse> {
    const response = await apiClient.get<MovieDetailResponse>(`/api/v1/movies/${id}`)
    return response.data
  },

  async getRecommendations(limit = 6): Promise<MovieRecommendationResponse> {
    const response = await apiClient.get<MovieRecommendationResponse>('/api/v1/movies/recommendations', {
      params: { limit },
    })
    return response.data
  },

  // Admin
  async getAdminMovies(params?: {
    q?: string
    genre?: string
    status?: string
    includeDeleted?: boolean
    page?: number
    size?: number
    sort?: string
  }): Promise<PageResponse<MovieSummaryResponse>> {
    const response = await apiClient.get<PageResponse<MovieSummaryResponse>>('/api/v1/admin/movies', {
      params,
    })
    return response.data
  },

  async createMovie(payload: any): Promise<MovieDetailResponse> {
    const response = await apiClient.post<MovieDetailResponse>('/api/v1/admin/movies', payload)
    return response.data
  },

  async updateMovie(id: string, payload: any): Promise<MovieDetailResponse> {
    const response = await apiClient.put<MovieDetailResponse>(`/api/v1/admin/movies/${id}`, payload)
    return response.data
  },

  async deleteMovie(id: string): Promise<void> {
    await apiClient.delete(`/api/v1/admin/movies/${id}`)
  },
}

export default movieService

