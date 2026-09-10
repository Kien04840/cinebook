import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import type { ApiError } from '@/types/api.types'
import type { AuthResponse } from '@/types/auth.types'

/**
 * Cấu hình Axios HTTP Client trung tâm của ứng dụng Frontend CineBook.
 * 
 * Kiến trúc & Xử lý mạng:
 * 1. Single Public Origin Proxy:
 *    - Sử dụng đường dẫn tương đối (BASE_URL = '') khi chạy qua Vite Proxy để tự động forward
 *      các request /api sang Backend Spring Boot (:8080) mà không gặp lỗi CORS hay Mixed Content.
 * 2. Request Interceptor:
 *    - Tự động lấy Access Token từ LocalStorage và đính kèm vào Header `Authorization: Bearer <token>`.
 * 3. Response Interceptor & Silent Refresh Token Queue:
 *    - Khi nhận mã lỗi HTTP 401 Unauthorized (Access Token 15 phút đã hết hạn):
 *      + Áp dụng hàng đợi (failedQueue) để chặn các request đồng thời, chỉ gọi API /refresh một lần duy nhất.
 *      + Sau khi nhận được Access Token mới, giải phóng hàng đợi và tự động thực thi lại các request bị lỗi ban đầu.
 *      + Nếu Refresh Token cũng hết hạn (7 ngày), tự động dọn dẹp LocalStorage và chuyển người dùng về trang đăng nhập.
 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

// Hàng đợi lưu các request bị tạm hoãn khi đang trong quá trình refresh token
let isRefreshing = false
let failedQueue: Array<{
  resolve: (value?: unknown) => void
  reject: (reason?: unknown) => void
}> = []

const processQueue = (error: Error | null, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error)
    } else {
      promise.resolve(token)
    }
  })
  failedQueue = []
}

// Request Interceptor: Tự động đính kèm Access Token vào Header Authorization
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('cinebook_access_token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response Interceptor: Xử lý phản hồi và tự động làm mới token (Silent Token Refresh)
apiClient.interceptors.response.use(
  (response) => {
    return response
  },
  async (error: AxiosError<ApiError>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // Bắt mã lỗi 401 Unauthorized khi Access Token hết hạn
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      // Bỏ qua nếu chính endpoint login hoặc refresh bị lỗi 401 (chống lặp vô hạn)
      if (
        originalRequest.url?.includes('/api/v1/auth/login') ||
        originalRequest.url?.includes('/api/v1/auth/refresh')
      ) {
        return Promise.reject(error)
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        })
          .then((token) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`
            }
            return apiClient(originalRequest)
          })
          .catch((err) => Promise.reject(err))
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = localStorage.getItem('cinebook_refresh_token')
      if (!refreshToken) {
        isRefreshing = false
        clearAuthStorage()
        window.dispatchEvent(new CustomEvent('auth:expired'))
        return Promise.reject(error)
      }

      try {
        const refreshResponse = await axios.post<AuthResponse>(
          `${BASE_URL}/api/v1/auth/refresh`,
          { refreshToken }
        )

        const newAccessToken = refreshResponse.data.accessToken
        const newRefreshToken = refreshResponse.data.refreshToken || refreshToken

        localStorage.setItem('cinebook_access_token', newAccessToken)
        localStorage.setItem('cinebook_refresh_token', newRefreshToken)

        if (refreshResponse.data.user) {
          localStorage.setItem('cinebook_user', JSON.stringify(refreshResponse.data.user))
        }

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        }

        processQueue(null, newAccessToken)
        return apiClient(originalRequest)
      } catch (refreshErr) {
        processQueue(refreshErr as Error, null)
        clearAuthStorage()
        window.dispatchEvent(new CustomEvent('auth:expired'))
        return Promise.reject(refreshErr)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)

function clearAuthStorage() {
  localStorage.removeItem('cinebook_access_token')
  localStorage.removeItem('cinebook_refresh_token')
  localStorage.removeItem('cinebook_user')
}

export { getErrorMessage, getApiErrorMessage } from '@/utils/error'

export default apiClient
