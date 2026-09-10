import apiClient from './api'
import type { GenreResponse, CreateGenreRequest, UpdateGenreRequest } from '@/types/genre.types'
import type { TmdbGenreSyncResponse } from '@/types/tmdb.types'

let cachedGenresPromise: Promise<GenreResponse[]> | null = null

export const genreService = {
  /**
   * Lấy danh sách toàn bộ thể loại (Public endpoint)
   * Sử dụng cơ chế cache promise trong bộ nhớ client, dùng forceRefresh = true để ép tải lại
   */
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

  /**
   * Lấy danh sách toàn bộ thể loại dành cho quản trị viên (Admin endpoint)
   * Luôn truy vấn trực tiếp không qua cache để đảm bảo tính thời sự của dữ liệu quản trị
   */
  async getAdminGenres(): Promise<GenreResponse[]> {
    const response = await apiClient.get<GenreResponse[]>('/api/v1/admin/genres')
    return response.data
  },

  /**
   * Tạo thể loại mới (Admin)
   */
  async createGenre(payload: CreateGenreRequest): Promise<GenreResponse> {
    const response = await apiClient.post<GenreResponse>('/api/v1/admin/genres', payload)
    cachedGenresPromise = null
    return response.data
  },

  /**
   * Cập nhật thông tin thể loại (Admin)
   */
  async updateGenre(id: string, payload: UpdateGenreRequest): Promise<GenreResponse> {
    const response = await apiClient.put<GenreResponse>(`/api/v1/admin/genres/${id}`, payload)
    cachedGenresPromise = null
    return response.data
  },

  /**
   * Xóa thể loại (Admin)
   */
  async deleteGenre(id: string): Promise<void> {
    await apiClient.delete(`/api/v1/admin/genres/${id}`)
    cachedGenresPromise = null
  },

  /**
   * Đồng bộ thể loại phim từ TMDB API (Admin)
   */
  async syncTmdbGenres(): Promise<TmdbGenreSyncResponse> {
    const response = await apiClient.post<TmdbGenreSyncResponse>('/api/v1/admin/tmdb/genres/sync')
    cachedGenresPromise = null
    return response.data
  },

  /**
   * Xóa bộ nhớ đệm thể loại
   */
  clearCache(): void {
    cachedGenresPromise = null
  },
}

export default genreService

