import apiClient from './api'
import type { PageResponse } from '@/types/api.types'
import type {
  NotificationResponse,
  UnreadCountResponse,
} from '@/types/notification.types'

export const notificationService = {
  /**
   * Lấy danh sách thông báo phân trang của người dùng hiện tại
   */
  async getNotifications(page = 0, size = 10): Promise<PageResponse<NotificationResponse>> {
    const response = await apiClient.get<PageResponse<NotificationResponse>>('/api/v1/notifications', {
      params: { page, size },
    })
    return response.data
  },

  /**
   * Lấy số lượng thông báo chưa đọc của người dùng hiện tại
   */
  async getUnreadCount(): Promise<UnreadCountResponse> {
    const response = await apiClient.get<UnreadCountResponse>('/api/v1/notifications/unread-count')
    return response.data
  },

  /**
   * Đánh dấu một thông báo là đã đọc
   */
  async markAsRead(id: string): Promise<NotificationResponse> {
    const response = await apiClient.patch<NotificationResponse>(`/api/v1/notifications/${id}/read`)
    return response.data
  },

  /**
   * Đánh dấu tất cả thông báo của người dùng là đã đọc
   */
  async markAllAsRead(): Promise<void> {
    await apiClient.patch<void>('/api/v1/notifications/read-all')
  },
}

